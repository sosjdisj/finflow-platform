package org.example.finflowbackend.payment.event;

/**
 * 支付创建事件详情（主题 finflow.payment.created）。
 */
public record PaymentCreatedPayload(Long orderId, String platformOrderNo, Long merchantId,
                                    String merchantOrderNo, Long amount, String paymentMethod) {
}
