package org.example.finflowbackend.payment.service;

import org.example.finflowbackend.channel.api.ChannelErrorCode;
import org.example.finflowbackend.channel.api.ChannelOperationsApi;
import org.example.finflowbackend.channel.api.ChannelRequest;
import org.example.finflowbackend.channel.api.ChannelResult;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.payment.PaymentErrorCode;
import org.example.finflowbackend.payment.api.PaymentProcessApi;
import org.example.finflowbackend.payment.api.PaymentStatus;
import org.example.finflowbackend.payment.api.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.dto.PaymentOrderDetailResponse;
import org.example.finflowbackend.payment.api.PaymentOrderResponse;
import org.example.finflowbackend.risk.api.RiskDecisionApi;
import org.example.finflowbackend.risk.api.RiskDecisionRequest;
import org.example.finflowbackend.risk.api.RiskDecisionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 支付主流程编排：创建订单（幂等，S13）→ 状态机推进（CREATED→PENDING）→ 风控决策（M09 单次接口调用）→
 * 渠道受理（channel 模块 ChannelOperationsApi，本地实现或独立渠道服务）→ 渠道异步回调由
 * ChannelCallbackService 落状态。
 * 风控 REJECT 订单置 FAILED 后抛出；REVIEW 停在 REVIEW 等人工处理（risk 模块调 resumeAfterReview 推进，
 * 通过后不再重查风控）；幂等回放（原单非 CREATED）不重走受理。
 * 跨模块（risk 人工审核结论回写）经 {@link PaymentProcessApi}。
 */
@Service
public class PaymentProcessService implements PaymentProcessApi {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessService.class);

    private final PaymentOrderService paymentOrderService;
    private final RiskDecisionApi riskDecision;
    private final ChannelOperationsApi channelOperations;

    public PaymentProcessService(PaymentOrderService paymentOrderService, RiskDecisionApi riskDecision,
                                 ChannelOperationsApi channelOperations) {
        this.paymentOrderService = paymentOrderService;
        this.riskDecision = riskDecision;
        this.channelOperations = channelOperations;
    }

    /** 建单并立即受理；重复提交返回原单（幂等），不重复受理。 */
    @Override
    public PaymentOrderResponse createAndProcess(PaymentOrderCreateRequest request) {
        PaymentOrderResponse order = paymentOrderService.create(request);
        if (PaymentStatus.CREATED.name().equals(order.status())) {
            process(order.id(), request.merchantId(), order.platformOrderNo(), order.amount(),
                    order.paymentMethod(), order.subject(), request.clientIp(), request.deviceId());
            return paymentOrderService.getDetail(order.id()).order();
        }
        // 幂等回放：原单已受理过，直接返回当前状态
        return order;
    }

    /** 单笔订单受理编排（建单后调用）。 */
    public void process(Long orderId, Long merchantId, String platformOrderNo, long amount,
                        String paymentMethod, String subject, String clientIp, String deviceId) {
        paymentOrderService.changeStatus(orderId, PaymentStatus.PENDING.name(), "发起支付，等待渠道受理", "PROCESS");
        try {
            RiskDecisionResponse decision = riskDecision.decide(
                    new RiskDecisionRequest(orderId, merchantId, amount, clientIp, deviceId, platformOrderNo));
            switch (decision.verdict()) {
                // 风控待审：停在 REVIEW，不路由渠道，人工通过/拒绝经 resumeAfterReview 推进
                case REVIEW -> {
                    paymentOrderService.changeStatus(orderId, PaymentStatus.REVIEW.name(),
                            "风控待人工审核: " + decision.reason(), "RISK");
                    return;
                }
                case REJECT -> throw new BusinessException(PaymentErrorCode.RISK_REJECTED, decision.reason());
                case ALLOW -> { /* 继续渠道路由 */ }
            }
            acceptByChannel(orderId, amount, paymentMethod, subject);
        } catch (BusinessException e) {
            // 受理失败订单终态化，异常继续上抛给调用方
            failSafely(orderId, "受理失败: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 风控 REVIEW 订单人工处理（risk 模块调用）：通过回 PENDING 并直接渠道路由（本次人工放行即风控结论，
     * 不再重查风控）；拒绝置 FAILED。订单不在 REVIEW 时由状态机拒绝，天然幂等。
     */
    @Override
    public void resumeAfterReview(Long orderId, boolean approved, String operator, String reason) {
        String remark = reason == null || reason.isBlank() ? "" : ": " + reason;
        if (approved) {
            paymentOrderService.changeStatus(orderId, PaymentStatus.PENDING.name(),
                    "风控人工审核通过" + remark, operator);
            PaymentOrderResponse order = paymentOrderService.getDetail(orderId).order();
            try {
                acceptByChannel(orderId, order.amount(), order.paymentMethod(), order.subject());
            } catch (BusinessException e) {
                failSafely(orderId, "受理失败: " + e.getMessage());
                throw e;
            }
        } else {
            paymentOrderService.changeStatus(orderId, PaymentStatus.FAILED.name(),
                    "风控人工审核拒绝" + remark, operator);
        }
    }

    /** 渠道路由 + 受理；渠道未受理抛异常由调用方终态化。 */
    private void acceptByChannel(Long orderId, long amount, String paymentMethod, String subject) {
        // 渠道受理需平台单号做幂等、商户单号供对账，从订单读取补齐
        PaymentOrderResponse order = paymentOrderService.get(orderId);
        ChannelResult result = channelOperations.createPayment(new ChannelRequest(orderId, order.platformOrderNo(),
                order.merchantOrderNo(), null, amount, paymentMethod, subject, null));
        if (!result.accepted()) {
            throw new BusinessException(ChannelErrorCode.CHANNEL_NOT_FOUND, result.message());
        }
    }

    /** 关单/失败兜底：置 FAILED 失败仅记日志（如已被回调或关单，转换冲突属常态）。 */
    private void failSafely(Long orderId, String reason) {
        try {
            paymentOrderService.changeStatus(orderId, PaymentStatus.FAILED.name(), reason, "PROCESS");
        } catch (BusinessException e) {
            log.warn("订单受理失败后终态化冲突: orderId={}", orderId, e);
        }
    }
}
