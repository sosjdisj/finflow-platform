package org.example.finflowbackend.contract;

import java.time.LocalDateTime;

/**
 * 支付成功事件详情（主题 {@link EventTopics#PAYMENT_SUCCEEDED}）。
 * merchantId 为 M16 补齐：通知 / Webhook 服务不再回查订单，只用事件自带归属。
 */
public record PaymentSucceededPayload(Long orderId, Long merchantId, String platformOrderNo,
                                      Long amount, String currency, LocalDateTime paidAt) {
}
