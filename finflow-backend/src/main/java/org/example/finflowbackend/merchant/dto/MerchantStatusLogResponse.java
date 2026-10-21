package org.example.finflowbackend.merchant.dto;

import org.example.finflowbackend.merchant.entity.MerchantStatusLog;

import java.time.LocalDateTime;

/**
 * 商户状态变更记录。
 */
public record MerchantStatusLogResponse(Long id, String fromStatus, String toStatus,
                                        String reason, String operator, LocalDateTime createdAt) {

    public static MerchantStatusLogResponse from(MerchantStatusLog logEntry) {
        return new MerchantStatusLogResponse(logEntry.getId(), logEntry.getFromStatus(), logEntry.getToStatus(),
                logEntry.getReason(), logEntry.getOperator(), logEntry.getCreatedAt());
    }
}
