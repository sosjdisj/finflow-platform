package org.example.finflowbackend.merchant.dto;

import org.example.finflowbackend.merchant.entity.MerchantRateLog;

import java.time.LocalDateTime;

/**
 * 费率变更历史记录。
 */
public record MerchantRateLogResponse(Long id, Long oldRateBps, Long oldFixedFee,
                                      Long newRateBps, Long newFixedFee,
                                      String operator, LocalDateTime createdAt) {

    public static MerchantRateLogResponse from(MerchantRateLog logEntry) {
        return new MerchantRateLogResponse(logEntry.getId(), logEntry.getOldRateBps(), logEntry.getOldFixedFee(),
                logEntry.getNewRateBps(), logEntry.getNewFixedFee(), logEntry.getOperator(), logEntry.getCreatedAt());
    }
}
