package org.example.finflowbackend.refund.dto;

import org.example.finflowbackend.refund.entity.RefundOrderStateLog;

import java.time.LocalDateTime;

/**
 * 退款状态留痕信息。
 */
public record RefundStateLogResponse(Long id, Long refundId, String fromStatus, String toStatus,
                                     String reason, String operator, LocalDateTime createdAt) {

    public static RefundStateLogResponse from(RefundOrderStateLog log) {
        return new RefundStateLogResponse(log.getId(), log.getRefundId(), log.getFromStatus(),
                log.getToStatus(), log.getReason(), log.getOperator(), log.getCreatedAt());
    }
}