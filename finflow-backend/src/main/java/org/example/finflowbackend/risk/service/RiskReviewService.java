package org.example.finflowbackend.risk.service;

import org.example.finflowbackend.payment.PaymentStatus;
import org.example.finflowbackend.payment.dto.PaymentOrderResponse;
import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.example.finflowbackend.payment.service.PaymentProcessService;
import org.example.finflowbackend.risk.RiskErrorCode;
import org.example.finflowbackend.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 风控 REVIEW 订单人工处理（S30）：通过 → 订单回 PENDING 并直接渠道路由（人工放行即风控结论，
 * 不重查风控）；拒绝 → 订单 FAILED。推进由 payment 受理编排完成，本服务只做归属与状态预检。
 */
@Service
public class RiskReviewService {

    private final PaymentOrderService paymentOrderService;
    private final PaymentProcessService paymentProcessService;

    public RiskReviewService(PaymentOrderService paymentOrderService, PaymentProcessService paymentProcessService) {
        this.paymentOrderService = paymentOrderService;
        this.paymentProcessService = paymentProcessService;
    }

    @Transactional
    public void approve(Long orderId, String operator, String remark) {
        requireReviewOrder(orderId);
        paymentProcessService.resumeAfterReview(orderId, true, operator, remark);
    }

    @Transactional
    public void reject(Long orderId, String operator, String remark) {
        requireReviewOrder(orderId);
        paymentProcessService.resumeAfterReview(orderId, false, operator, remark);
    }

    private void requireReviewOrder(Long orderId) {
        PaymentOrderResponse order = paymentOrderService.get(orderId);
        if (!PaymentStatus.REVIEW.name().equals(order.status())) {
            throw new BusinessException(RiskErrorCode.ORDER_NOT_IN_REVIEW,
                    "订单当前状态 " + order.status() + "，不在风控待审状态");
        }
    }
}
