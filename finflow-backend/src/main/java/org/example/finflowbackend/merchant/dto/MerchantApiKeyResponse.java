package org.example.finflowbackend.merchant.dto;

import org.example.finflowbackend.merchant.entity.MerchantApiKey;

import java.time.LocalDateTime;

/**
 * 凭证信息（不含 Secret）。
 */
public record MerchantApiKeyResponse(Long id, Long merchantId, String apiKey,
                                     String ipWhitelist, LocalDateTime createdAt) {

    public static MerchantApiKeyResponse from(MerchantApiKey apiKey) {
        return new MerchantApiKeyResponse(apiKey.getId(), apiKey.getMerchantId(), apiKey.getApiKey(),
                apiKey.getIpWhitelist(), apiKey.getCreatedAt());
    }
}
