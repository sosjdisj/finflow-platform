package org.example.finflowbackend.merchant.dto;

import java.time.LocalDateTime;

/**
 * 商户当前费率。
 */
public record MerchantRateResponse(Long merchantId, Long rateBps, Long fixedFee, LocalDateTime updatedAt) {

    public static MerchantRateResponse from(org.example.finflowbackend.merchant.entity.MerchantRate rate) {
        return new MerchantRateResponse(rate.getMerchantId(), rate.getRateBps(), rate.getFixedFee(),
                rate.getUpdatedAt());
    }
}
