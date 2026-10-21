package org.example.finflowbackend.settlement.dto;

import org.example.finflowbackend.settlement.entity.SettlementOrderStateLog;

import java.time.LocalDateTime;

/**
 * 结算单状态变更记录。
 */
public record SettlementStateLogResponse(String fromStatus, String toStatus,
                                         String reason, String operator, LocalDateTime createdAt) {

    public static SettlementStateLogResponse from(SettlementOrderStateLog log) {
        return new SettlementStateLogResponse(log.getFromStatus(), log.getToStatus(),
                log.getReason(), log.getOperator(), log.getCreatedAt());
    }
}
