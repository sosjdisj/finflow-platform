package org.example.finflowbackend.contract;

/**
 * 支付创建事件详情（主题 {@link EventTopics#PAYMENT_CREATED}）。
 */
public record PaymentCreatedPayload(Long orderId, String platformOrderNo, Long merchantId,
                                    String merchantOrderNo, Long amount, String paymentMethod) {
}
