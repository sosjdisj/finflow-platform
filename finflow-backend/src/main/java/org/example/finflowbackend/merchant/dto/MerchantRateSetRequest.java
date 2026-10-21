package org.example.finflowbackend.merchant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 费率设置请求：比例费率与固定手续费至少启用一种；金额一律整数（万分比 / 分）。
 */
public record MerchantRateSetRequest(
        @Min(0) @Max(10000) Long rateBps,
        @Min(0) Long fixedFee) {
}
