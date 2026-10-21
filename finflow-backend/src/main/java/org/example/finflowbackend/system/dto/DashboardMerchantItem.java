package org.example.finflowbackend.system.dto;

/**
 * 商户交易额 TOP 项。
 */
public record DashboardMerchantItem(Long merchantId, String merchantName, long amount) {
}
