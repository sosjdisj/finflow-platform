package org.example.finflowbackend.payment.event;

import java.time.LocalDateTime;

/**
 * 支付失败事件详情（主题 finflow.payment.failed）。渠道失败与超时关单共用，
 * reason 区分具体原因（超时关单的终态同样视作支付失败）。
 */
public record PaymentFailedPayload(Long orderId, String platformOrderNo, Long amount,
                                   String reason, LocalDateTime failedAt) {
}
