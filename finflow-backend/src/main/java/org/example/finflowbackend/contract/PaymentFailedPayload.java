package org.example.finflowbackend.contract;

import java.time.LocalDateTime;

/**
 * 支付失败事件详情（主题 {@link EventTopics#PAYMENT_FAILED}）。渠道失败与超时关单共用，
 * reason 区分具体原因（超时关单的终态同样视作支付失败）。
 * merchantId 为 M16 补齐：通知 / Webhook 服务不再回查订单。
 */
public record PaymentFailedPayload(Long orderId, Long merchantId, String platformOrderNo,
                                   Long amount, String reason, LocalDateTime failedAt) {
}
