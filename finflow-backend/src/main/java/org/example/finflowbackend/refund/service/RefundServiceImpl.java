package org.example.finflowbackend.refund.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.channel.api.ChannelOperationsApi;
import org.example.finflowbackend.channel.api.ChannelRequest;
import org.example.finflowbackend.channel.api.ChannelResult;
import org.example.finflowbackend.channel.api.PaymentTransactionApi;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.payment.api.PaymentOrderApi;
import org.example.finflowbackend.payment.api.PaymentOrderResponse;
import org.example.finflowbackend.payment.api.PaymentOrderSnapshot;
import org.example.finflowbackend.payment.api.PaymentStatus;
import org.example.finflowbackend.payment.api.RefundResultHandler;
import org.example.finflowbackend.refund.api.RefundErrorCode;
import org.example.finflowbackend.refund.api.RefundOrderSnapshot;
import org.example.finflowbackend.refund.RefundStatus;
import org.example.finflowbackend.refund.dto.RefundCreateRequest;
import org.example.finflowbackend.refund.dto.RefundOrderDetailResponse;
import org.example.finflowbackend.refund.dto.RefundOrderResponse;
import org.example.finflowbackend.refund.dto.RefundStateLogResponse;
import org.example.finflowbackend.refund.entity.RefundOrder;
import org.example.finflowbackend.refund.entity.RefundOrderStateLog;
import org.example.finflowbackend.refund.mapper.RefundOrderMapper;
import org.example.finflowbackend.refund.mapper.RefundOrderStateLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 退款编排（S21）：
 * 创建 = 占用可退额度（行锁防并发超退）→ 落退款单 APPLYING → 渠道受理 → PROCESSING；
 * 渠道异步回调（RefundResultHandler）= 成功终态化并发退款成功事件，失败终态化并释放额度、
 * 无在途退款时支付订单回退 PAID。
 * 资金流约定（第一版）：退款成功 → 平台备付金 − 退款金额（ledger 模块消费事件入账）。
 */
@Service
public class RefundServiceImpl implements RefundService, RefundResultHandler {

    private static final Logger log = LoggerFactory.getLogger(RefundServiceImpl.class);
    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefundOrderMapper refundMapper;
    private final RefundOrderStateLogMapper stateLogMapper;
    private final PaymentOrderApi paymentOrderApi;
    private final PaymentTransactionApi paymentTransactionApi;
    private final ChannelOperationsApi channelOperations;
    private final RefundStatusService refundStatusService;

    public RefundServiceImpl(RefundOrderMapper refundMapper, RefundOrderStateLogMapper stateLogMapper,
                             PaymentOrderApi paymentOrderApi, PaymentTransactionApi paymentTransactionApi,
                             ChannelOperationsApi channelOperations, RefundStatusService refundStatusService) {
        this.refundMapper = refundMapper;
        this.stateLogMapper = stateLogMapper;
        this.paymentOrderApi = paymentOrderApi;
        this.paymentTransactionApi = paymentTransactionApi;
        this.channelOperations = channelOperations;
        this.refundStatusService = refundStatusService;
    }

    @Override
    public RefundOrderResponse create(RefundCreateRequest request, String operator) {
        // ① 行锁占用可退额度（自身事务提交）：状态与剩余可退校验、申请即占用、首笔 PAID→REFUNDING
        PaymentOrderSnapshot order = paymentOrderApi.reserveRefundQuota(request.orderId(), request.amount(), operator);
        // ② 落退款单（单条 insert 原子）；失败补偿释放额度，防止额度泄漏
        RefundOrder refund;
        try {
            refund = insertApplying(order, request, operator);
        } catch (RuntimeException e) {
            paymentOrderApi.releaseRefundQuota(request.orderId(), request.amount());
            throw e;
        }
        // ③ 渠道受理（事务外，同 S15 编排；受理失败退款单终态化并释放额度）
        try {
            // 渠道退款需原支付渠道单号：从该订单的 PAYMENT 交互留痕取
            String channelOrderNo = paymentTransactionApi.findPaymentChannelOrderNo(order.id());
            ChannelResult result = channelOperations.refund(new ChannelRequest(order.id(), order.platformOrderNo(),
                    order.merchantOrderNo(), channelOrderNo, request.amount(), order.paymentMethod(),
                    order.subject(), refund.getRefundNo()));
            if (!result.accepted()) {
                throw new BusinessException(RefundErrorCode.CHANNEL_REFUND_REJECTED, result.message());
            }
            refundStatusService.markAccepted(refund.getId(), result.channelCode(), result.channelOrderNo());
            return RefundOrderResponse.from(requireRefund(refund.getId()));
        } catch (BusinessException e) {
            failSafely(refund, "受理失败: " + e.getMessage(), operator);
            throw e;
        }
    }

    @Override
    public RefundOrderSnapshot createRefund(Long orderId, long amount, String reason, String operator) {
        return RefundOrderSnapshot.from(create(new RefundCreateRequest(orderId, amount, reason), operator));
    }

    @Override
    public RefundOrderSnapshot findByRefundNo(String refundNo) {
        RefundOrder refund = refundNo == null || refundNo.isBlank() ? null
                : refundMapper.selectOne(Wrappers.<RefundOrder>query().eq("refund_no", refundNo));
        if (refund == null) {
            throw new BusinessException(RefundErrorCode.REFUND_NOT_FOUND);
        }
        return RefundOrderSnapshot.from(RefundOrderResponse.from(refund));
    }

    @Override
    public PageResponse<RefundOrderResponse> page(long page, long size, Long orderId, Long merchantId,
                                                  String refundNo, String status,
                                                  LocalDateTime createdFrom, LocalDateTime createdTo) {
        Page<RefundOrder> result = refundMapper.selectPage(new Page<>(page, size),
                Wrappers.<RefundOrder>query()
                        .eq(orderId != null, "order_id", orderId)
                        .eq(merchantId != null, "merchant_id", merchantId)
                        .like(refundNo != null && !refundNo.isBlank(), "refund_no", refundNo)
                        .eq(status != null && !status.isBlank(), "status", status)
                        .ge(createdFrom != null, "created_at", createdFrom)
                        .le(createdTo != null, "created_at", createdTo)
                        .orderByDesc("id"));
        return PageResponse.of(result.convert(RefundOrderResponse::from));
    }

    @Override
    public RefundOrderDetailResponse getDetail(Long id) {
        RefundOrder refund = requireRefund(id);
        List<RefundStateLogResponse> logs = stateLogMapper.selectList(
                        Wrappers.<RefundOrderStateLog>query().eq("refund_id", id).orderByAsc("id"))
                .stream().map(RefundStateLogResponse::from).toList();
        return new RefundOrderDetailResponse(RefundOrderResponse.from(refund), logs);
    }

    /**
     * 渠道退款结果回调（payment 模块 ChannelCallbackService 委托）。
     * 同一事务内完成：退款单终态化 + 支付订单额度释放/退满联动；事件由推进组件在事务提交后发布。
     * 回调可能先于受理响应落库（APPLYING 竞态），此时先补受理（回填渠道要素）再处理结果；
     * 已终态（SUCCESS/FAILED/CLOSED）为重复回调，幂等忽略。
     */
    @Override
    @Transactional
    public void onResult(Long orderId, String channelCode, String refundNo, String channelRefundNo,
                         boolean success, String message) {
        RefundOrder refund = refundMapper.selectOne(
                Wrappers.<RefundOrder>query().eq("refund_no", refundNo));
        if (refund == null) {
            log.warn("[退款回调] 退款单不存在，忽略: refundNo={}, channelRefundNo={}", refundNo, channelRefundNo);
            return;
        }
        String status = refund.getStatus();
        if (isTerminal(status)) {
            log.info("[退款回调] 退款单已终态，忽略回调: refundNo={}, status={}", refundNo, status);
            return;
        }
        if (RefundStatus.APPLYING.name().equals(status)) {
            try {
                refundStatusService.markAccepted(refund.getId(), channelCode, channelRefundNo);
            } catch (BusinessException e) {
                // 受理路径并发推进失败 → 重读，仍非终态则按原状态继续处理结果
                RefundOrder latest = refundMapper.selectById(refund.getId());
                if (latest == null || isTerminal(latest.getStatus())) {
                    log.info("[退款回调] 补受理冲突且已终态，忽略: refundNo={}", refundNo);
                    return;
                }
            }
        }
        if (success) {
            refundStatusService.transition(refund.getId(), RefundStatus.SUCCESS,
                    "渠道回调退款成功: " + channelRefundNo, "CHANNEL:" + channelCode);
            // 退满联动 REFUNDING→REFUNDED（同事务，行锁内判断）
            paymentOrderApi.markFullyRefundedIfNeeded(orderId, refundNo, "REFUND:" + refundNo);
            log.info("[退款回调] 退款成功: refundNo={}, amount={}", refundNo, refund.getRefundAmount());
        } else {
            refundStatusService.transition(refund.getId(), RefundStatus.FAILED, message, "CHANNEL:" + channelCode);
            // 释放占用额度；无其余在途退款时支付订单回退 PAID
            paymentOrderApi.releaseRefundQuota(orderId, refund.getRefundAmount());
            revertToPaidIfNoInflight(orderId, refundNo);
            log.info("[退款回调] 退款失败: refundNo={}, reason={}", refundNo, message);
        }
    }

    private boolean isTerminal(String status) {
        return RefundStatus.SUCCESS.name().equals(status)
                || RefundStatus.FAILED.name().equals(status)
                || RefundStatus.CLOSED.name().equals(status);
    }

    /** 退款失败后：无在途退款且未退满时支付订单 REFUNDING→PAID（在 onResult 事务内）。 */
    private void revertToPaidIfNoInflight(Long orderId, String failedRefundNo) {
        Long inflight = refundMapper.selectCount(Wrappers.<RefundOrder>query()
                .eq("order_id", orderId)
                .in("status", List.of(RefundStatus.APPLYING.name(), RefundStatus.PROCESSING.name())));
        if (inflight != null && inflight > 0) {
            return;
        }
        PaymentOrderResponse order = paymentOrderApi.get(orderId);
        if (order.refundedAmount() < order.amount()) {
            paymentOrderApi.changeStatus(orderId, PaymentStatus.PAID.name(),
                    "退款失败释放额度，无在途退款: " + failedRefundNo, "REFUND:" + failedRefundNo);
        }
    }

    /** 受理失败兜底：仍 APPLYING 才终态化并释放额度（渠道回调已先推进则跳过），冲突属常态仅记日志。 */
    private void failSafely(RefundOrder refund, String reason, String operator) {
        try {
            RefundOrder current = refundMapper.selectById(refund.getId());
            if (current == null || !RefundStatus.APPLYING.name().equals(current.getStatus())) {
                return;
            }
            refundStatusService.transition(refund.getId(), RefundStatus.FAILED, reason, operator);
            paymentOrderApi.releaseRefundQuota(refund.getOrderId(), refund.getRefundAmount());
            // 受理失败且无其他在途退款时，支付订单从 REFUNDING 回退 PAID（与回调失败路径一致）
            revertToPaidIfNoInflight(refund.getOrderId(), refund.getRefundNo());
        } catch (BusinessException e) {
            log.warn("退款受理失败后终态化冲突: refundNo={}", refund.getRefundNo(), e);
        }
    }

    private RefundOrder insertApplying(PaymentOrderSnapshot order, RefundCreateRequest request, String operator) {
        RefundOrder refund = new RefundOrder();
        refund.setRefundNo(nextRefundNo());
        refund.setOrderId(order.id());
        refund.setMerchantId(order.merchantId());
        refund.setPlatformOrderNo(order.platformOrderNo());
        refund.setRefundAmount(request.amount());
        refund.setCurrency(order.currency());
        refund.setReason(request.reason());
        refund.setStatus(RefundStatus.APPLYING.name());
        refund.setOperator(operator);
        refundMapper.insert(refund);
        return refund;
    }

    /** 平台退款单号：R + 日期 + 6 位随机数字，唯一索引兜底，碰撞时重试。 */
    private String nextRefundNo() {
        String date = LocalDate.now().format(NO_DATE);
        for (int i = 0; i < 3; i++) {
            String no = "R" + date + (100000 + RANDOM.nextInt(900000));
            Long exists = refundMapper.selectCount(Wrappers.<RefundOrder>query().eq("refund_no", no));
            if (exists == null || exists == 0) {
                return no;
            }
        }
        throw new BusinessException(org.example.finflowbackend.common.api.ErrorCode.SYSTEM_ERROR, "退款单号生成失败，请重试");
    }

    private RefundOrder requireRefund(Long id) {
        RefundOrder refund = refundMapper.selectById(id);
        if (refund == null) {
            throw new BusinessException(RefundErrorCode.REFUND_NOT_FOUND);
        }
        return refund;
    }
}
