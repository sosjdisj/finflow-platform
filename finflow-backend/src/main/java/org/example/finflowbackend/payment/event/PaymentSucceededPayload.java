package org.example.finflowbackend.payment.event;

import java.time.LocalDateTime;

/**
 * 支付成功事件详情（主题 finflow.payment.succeeded）。
 */
public record PaymentSucceededPayload(Long orderId, String platformOrderNo, Long amount,
                                      String currency, LocalDateTime paidAt) {
}
