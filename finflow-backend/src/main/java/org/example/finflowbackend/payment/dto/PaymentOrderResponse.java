package org.example.finflowbackend.payment.dto;

import org.example.finflowbackend.payment.entity.PaymentOrder;

import java.time.LocalDateTime;

/**
 * 支付订单信息。
 */
public record PaymentOrderResponse(Long id, Long merchantId, String merchantOrderNo, String platformOrderNo,
                                   Long amount, Long refundedAmount, String currency, String subject, String body,
                                   String paymentMethod, String callbackUrl, LocalDateTime expiredAt,
                                   String status, LocalDateTime createdAt) {

    public static PaymentOrderResponse from(PaymentOrder order) {
        return new PaymentOrderResponse(order.getId(), order.getMerchantId(), order.getMerchantOrderNo(),
                order.getPlatformOrderNo(), order.getAmount(), order.getRefundedAmount(), order.getCurrency(),
                order.getSubject(), order.getBody(), order.getPaymentMethod(), order.getCallbackUrl(),
                order.getExpiredAt(), order.getStatus(), order.getCreatedAt());
    }
}