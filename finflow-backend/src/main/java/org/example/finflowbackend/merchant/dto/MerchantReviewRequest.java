package org.example.finflowbackend.merchant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 商户审核请求：通过 → ACTIVE，驳回 → CLOSED；意见记入状态留痕。
 */
public record MerchantReviewRequest(
        @NotNull(message = "审核结论不能为空") Boolean approve,
        @Size(max = 255) String opinion) {
}
