package org.example.finflowbackend.refund.event;

import java.time.LocalDateTime;

/**
 * 退款成功事件详情（主题 finflow.refund.succeeded）。
 * 账务消费者据此从平台备付金出账（bizNo=refundNo 幂等）。
 */
public record RefundSucceededPayload(Long orderId, String refundNo, String platformOrderNo,
                                     Long merchantId, Long refundAmount, String currency,
                                     LocalDateTime refundedAt) {
}
