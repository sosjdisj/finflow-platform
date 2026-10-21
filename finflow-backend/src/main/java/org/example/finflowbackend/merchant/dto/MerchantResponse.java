package org.example.finflowbackend.merchant.dto;

import org.example.finflowbackend.merchant.entity.Merchant;

import java.time.LocalDateTime;

/**
 * 商户信息。
 */
public record MerchantResponse(Long id, String merchantNo, String merchantName, String category,
                               String contactName, String contactPhone, String contactEmail,
                               String businessLicenseNo, String businessAddress, String settleCycle,
                               String webhookUrl, String status, LocalDateTime createdAt) {

    public static MerchantResponse from(Merchant merchant) {
        return new MerchantResponse(merchant.getId(), merchant.getMerchantNo(), merchant.getMerchantName(),
                merchant.getCategory(), merchant.getContactName(), merchant.getContactPhone(),
                merchant.getContactEmail(), merchant.getBusinessLicenseNo(), merchant.getBusinessAddress(),
                merchant.getSettleCycle(), merchant.getWebhookUrl(), merchant.getStatus(), merchant.getCreatedAt());
    }
}
