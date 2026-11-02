package org.example.finflowbackend.contract;

import java.time.LocalDateTime;

/**
 * 退款成功事件详情（主题 {@link EventTopics#REFUND_SUCCEEDED}）。
 * 账务消费者据此从平台备付金出账（bizNo=refundNo 幂等）。
 */
public record RefundSucceededPayload(Long orderId, String refundNo, String platformOrderNo,
                                     Long merchantId, Long refundAmount, String currency,
                                     LocalDateTime refundedAt) {
}
