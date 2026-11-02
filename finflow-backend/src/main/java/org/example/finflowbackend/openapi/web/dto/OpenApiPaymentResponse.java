package org.example.finflowbackend.openapi.web.dto;

import org.example.finflowbackend.payment.api.PaymentOrderResponse;

import java.time.LocalDateTime;

/**
 * OpenAPI 支付订单视图（对外契约）：只暴露商户关心的字段，
 * 与后台详情 / 内部实体解耦，后台字段调整不会改变商户契约。
 */
public record OpenApiPaymentResponse(String merchantOrderNo, String platformOrderNo, Long amount,
                                     Long refundedAmount, String currency, String subject,
                                     String paymentMethod, String status,
                                     LocalDateTime expiredAt, LocalDateTime createdAt) {

    public static OpenApiPaymentResponse from(PaymentOrderResponse order) {
        return new OpenApiPaymentResponse(order.merchantOrderNo(), order.platformOrderNo(), order.amount(),
                order.refundedAmount(), order.currency(), order.subject(), order.paymentMethod(),
                order.status(), order.expiredAt(), order.createdAt());
    }
}
