package org.example.finflowbackend.payment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.common.api.ErrorCode;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.merchant.MerchantStatus;
import org.example.finflowbackend.merchant.dto.MerchantDetailResponse;
import org.example.finflowbackend.merchant.service.MerchantService;
import org.example.finflowbackend.payment.PaymentErrorCode;
import org.example.finflowbackend.payment.PaymentStatus;
import org.example.finflowbackend.payment.dto.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.dto.PaymentOrderDetailResponse;
import org.example.finflowbackend.payment.dto.PaymentOrderResponse;
import org.example.finflowbackend.payment.dto.PaymentOrderStateLogResponse;
import org.example.finflowbackend.payment.dto.PaymentTransactionResponse;
import org.example.finflowbackend.payment.entity.PaymentOrder;
import org.example.finflowbackend.payment.entity.PaymentOrderStateLog;
import org.example.finflowbackend.payment.event.PaymentEventPublisher;
import org.example.finflowbackend.payment.mapper.PaymentOrderMapper;
import org.example.finflowbackend.payment.mapper.PaymentOrderStateLogMapper;
import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.example.finflowbackend.payment.service.PaymentTransactionService;
import org.example.finflowbackend.refund.RefundErrorCode;
import org.example.finflowbackend.system.config.ConfigKeys;
import org.example.finflowbackend.system.service.ConfigService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 支付订单实现。
 * 状态约定集中在 PaymentStatus.MACHINE；跨模块仅经 MerchantService 取商户，不直接读商户表。
 */
@Service
public class PaymentOrderServiceImpl implements PaymentOrderService {

    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentOrderMapper orderMapper;
    private final PaymentOrderStateLogMapper stateLogMapper;
    private final PaymentTransactionService transactionService;
    private final MerchantService merchantService;
    private final PaymentEventPublisher paymentEventPublisher;
    private final ConfigService configService;

    public PaymentOrderServiceImpl(PaymentOrderMapper orderMapper, PaymentOrderStateLogMapper stateLogMapper,
                                   PaymentTransactionService transactionService, MerchantService merchantService,
                                   PaymentEventPublisher paymentEventPublisher, ConfigService configService) {
        this.orderMapper = orderMapper;
        this.stateLogMapper = stateLogMapper;
        this.transactionService = transactionService;
        this.merchantService = merchantService;
        this.paymentEventPublisher = paymentEventPublisher;
        this.configService = configService;
    }

    @Override
    public PaymentOrderResponse create(PaymentOrderCreateRequest request) {
        // 系统开关与全局限额（S37 配置中心，改配置即生效）
        if (!configService.getBool(ConfigKeys.SWITCH_PAYMENT_CREATE)) {
            throw new BusinessException(PaymentErrorCode.SWITCH_OFF);
        }
        if (request.amount() > configService.getLong(ConfigKeys.PAYMENT_SINGLE_LIMIT)) {
            throw new BusinessException(PaymentErrorCode.AMOUNT_OVER_LIMIT);
        }
        // 商户不存在时由 merchant 模块抛 43001；仅生效中商户可发起支付
        MerchantDetailResponse merchant = merchantService.getDetail(request.merchantId());
        if (!MerchantStatus.ACTIVE.name().equals(merchant.merchant().status())) {
            throw new BusinessException(PaymentErrorCode.MERCHANT_NOT_ACTIVE);
        }

        PaymentOrder order = new PaymentOrder();
        order.setMerchantId(request.merchantId());
        order.setMerchantOrderNo(request.merchantOrderNo());
        order.setPlatformOrderNo(nextPlatformOrderNo());
        order.setAmount(request.amount());
        order.setCurrency(request.currency() == null || request.currency().isBlank() ? "CNY" : request.currency());
        order.setSubject(request.subject());
        order.setBody(request.body());
        order.setPaymentMethod(request.paymentMethod());
        order.setCallbackUrl(request.callbackUrl());
        order.setExpiredAt(resolveExpiredAt(request.expiredAt()));
        order.setStatus(PaymentStatus.CREATED.name());
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // uk(merchant_id, merchant_order_no) 幂等兜底：并发重复建单返回已存在的原单
            PaymentOrder existing = orderMapper.selectOne(Wrappers.<PaymentOrder>query()
                    .eq("merchant_id", request.merchantId())
                    .eq("merchant_order_no", request.merchantOrderNo()));
            if (existing == null) {
                // 非业务键冲突（如平台单号撞车），原样上抛
                throw e;
            }
            return PaymentOrderResponse.from(existing);
        }
        return PaymentOrderResponse.from(order);
    }

    /** 状态推进：状态机校验 + CAS 更新（where 旧状态）+ 留痕，同事务；并发竞态后到者 CAS 失败拒绝。 */
    @Override
    @Transactional
    public void changeStatus(Long orderId, String toStatus, String reason, String operator) {
        PaymentOrder order = requireOrder(orderId);
        String fromStatus = order.getStatus();
        PaymentStatus.MACHINE.checkOrThrow(PaymentStatus.valueOf(fromStatus),
                PaymentStatus.valueOf(toStatus), PaymentErrorCode.ILLEGAL_STATUS_TRANSITION);
        int updated = orderMapper.update(null, Wrappers.<PaymentOrder>update()
                .set("status", toStatus)
                .eq("id", orderId)
                .eq("status", fromStatus));
        if (updated == 0) {
            // 状态被并发修改（回调与关单竞速等），基于旧状态的推进作废
            throw new BusinessException(PaymentErrorCode.ILLEGAL_STATUS_TRANSITION,
                    "订单状态已被并发修改，推进作废");
        }

        PaymentOrderStateLog stateLog = new PaymentOrderStateLog();
        stateLog.setOrderId(orderId);
        stateLog.setFromStatus(fromStatus);
        stateLog.setToStatus(toStatus);
        stateLog.setReason(reason);
        stateLog.setOperator(operator);
        stateLogMapper.insert(stateLog);

        // 事件在事务提交后发布（DomainEventPublisher 保证），转换失败不会产生脏事件；
        // 按 toStatus 分发（order 实体上仍是旧状态）
        paymentEventPublisher.publishFor(order, toStatus, reason);
    }

    @Override
    public PaymentOrderDetailResponse getDetail(Long id) {
        PaymentOrder order = requireOrder(id);
        List<PaymentOrderStateLogResponse> logs = stateLogMapper.selectList(
                        Wrappers.<PaymentOrderStateLog>query().eq("order_id", id).orderByAsc("id"))
                .stream().map(PaymentOrderStateLogResponse::from).toList();
        List<PaymentTransactionResponse> transactions = transactionService.listByOrder(id).stream()
                .map(PaymentTransactionResponse::from).toList();
        return new PaymentOrderDetailResponse(PaymentOrderResponse.from(order), logs, transactions);
    }

    @Override
    public PageResponse<PaymentOrderResponse> page(long page, long size, Long merchantId, String status, String keyword,
                                                   LocalDateTime createdFrom, LocalDateTime createdTo,
                                                   Long minAmount, Long maxAmount) {
        Page<PaymentOrder> result = orderMapper.selectPage(new Page<>(page, size),
                Wrappers.<PaymentOrder>query()
                        .eq(merchantId != null, "merchant_id", merchantId)
                        .eq(status != null && !status.isBlank(), "status", status)
                        .ge(createdFrom != null, "created_at", createdFrom)
                        .le(createdTo != null, "created_at", createdTo)
                        .ge(minAmount != null, "amount", minAmount)
                        .le(maxAmount != null, "amount", maxAmount)
                        .and(keyword != null && !keyword.isBlank(),
                                q -> q.like("merchant_order_no", keyword).or().like("platform_order_no", keyword))
                        .orderByDesc("id"));
        return PageResponse.of(result.convert(PaymentOrderResponse::from));
    }

    private PaymentOrder requireOrder(Long id) {
        PaymentOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private LocalDateTime resolveExpiredAt(LocalDateTime expiredAt) {
        if (expiredAt == null) {
            // 默认过期时间走配置中心（S37），改配置即生效
            return LocalDateTime.now().plusHours(configService.getLong(ConfigKeys.PAYMENT_ORDER_EXPIRE_HOURS));
        }
        if (!expiredAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(PaymentErrorCode.EXPIRED_AT_INVALID);
        }
        return expiredAt;
    }

    /** 平台订单号：P + 日期 + 6 位随机数字，唯一索引兜底，碰撞时重试。 */
    private String nextPlatformOrderNo() {
        String date = LocalDate.now().format(NO_DATE);
        for (int i = 0; i < 3; i++) {
            String no = "P" + date + (100000 + RANDOM.nextInt(900000));
            Long exists = orderMapper.selectCount(Wrappers.<PaymentOrder>query().eq("platform_order_no", no));
            if (exists == null || exists == 0) {
                return no;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "平台订单号生成失败，请重试");
    }

    @Override
    public PaymentOrderResponse get(Long id) {
        return PaymentOrderResponse.from(requireOrder(id));
    }

    @Override
    public Long findIdByPlatformOrderNo(String platformOrderNo) {
        if (platformOrderNo == null || platformOrderNo.isBlank()) {
            return null;
        }
        PaymentOrder order = orderMapper.selectOne(
                Wrappers.<PaymentOrder>query().eq("platform_order_no", platformOrderNo));
        return order == null ? null : order.getId();
    }

    @Override
    @Transactional
    public PaymentOrder reserveRefundQuota(Long orderId, long refundAmount, String operator) {
        // 行锁读：同订单的退款申请在此串行，防并发超退
        PaymentOrder order = orderMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND);
        }
        if (!PaymentStatus.PAID.name().equals(order.getStatus())
                && !PaymentStatus.REFUNDING.name().equals(order.getStatus())) {
            throw new BusinessException(RefundErrorCode.ORDER_NOT_REFUNDABLE);
        }
        long refunded = order.getRefundedAmount() == null ? 0L : order.getRefundedAmount();
        if (refunded + refundAmount > order.getAmount()) {
            throw new BusinessException(RefundErrorCode.REFUND_AMOUNT_EXCEED);
        }
        // 申请即占用（行锁内读-改-写，无并发窗口）；退款失败经 releaseRefundQuota 释放
        orderMapper.update(null, Wrappers.<PaymentOrder>update()
                .set("refunded_amount", refunded + refundAmount)
                .eq("id", orderId));
        if (PaymentStatus.PAID.name().equals(order.getStatus())) {
            // 首笔退款：进入退款中；已有退款在途时保持 REFUNDING 不重复推进
            changeStatus(orderId, PaymentStatus.REFUNDING.name(), "发起退款，占用可退额度", operator);
        }
        return order;
    }

    @Override
    @Transactional
    public void releaseRefundQuota(Long orderId, long refundAmount) {
        // 与占用对称：行锁内回退额度
        PaymentOrder order = orderMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND);
        }
        long refunded = order.getRefundedAmount() == null ? 0L : order.getRefundedAmount();
        orderMapper.update(null, Wrappers.<PaymentOrder>update()
                .set("refunded_amount", Math.max(0L, refunded - refundAmount))
                .eq("id", orderId));
    }

    @Override
    @Transactional
    public void markFullyRefundedIfNeeded(Long orderId, String refundNo, String operator) {
        PaymentOrder order = orderMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND);
        }
        long refunded = order.getRefundedAmount() == null ? 0L : order.getRefundedAmount();
        if (refunded == order.getAmount()) {
            changeStatus(orderId, PaymentStatus.REFUNDED.name(), "退款全额完成: " + refundNo, operator);
        }
    }
}
