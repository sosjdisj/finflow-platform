package org.example.finflowbackend.payment.dto;

import org.example.finflowbackend.payment.entity.PaymentOrderStateLog;

import java.time.LocalDateTime;

/**
 * 支付订单状态变更留痕。
 */
public record PaymentOrderStateLogResponse(Long id, Long orderId, String fromStatus, String toStatus,
                                           String reason, String operator, LocalDateTime createdAt) {

    public static PaymentOrderStateLogResponse from(PaymentOrderStateLog log) {
        return new PaymentOrderStateLogResponse(log.getId(), log.getOrderId(), log.getFromStatus(),
                log.getToStatus(), log.getReason(), log.getOperator(), log.getCreatedAt());
    }
}
