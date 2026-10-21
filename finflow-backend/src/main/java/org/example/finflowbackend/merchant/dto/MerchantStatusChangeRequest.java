package org.example.finflowbackend.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 商户状态变更请求：目标状态必须命中状态机合法转换，否则 43003。
 */
public record MerchantStatusChangeRequest(
        @NotBlank
        @Pattern(regexp = "PENDING|REVIEWING|ACTIVE|SUSPENDED|CLOSED", message = "目标状态不合法")
        String targetStatus,
        @Size(max = 255) String reason) {
}
