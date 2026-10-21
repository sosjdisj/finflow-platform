package org.example.finflowbackend.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.finflowbackend.common.validation.CreditCode;

/**
 * 创建商户请求：创建后状态从 PENDING 起。
 */
public record MerchantCreateRequest(
        @NotBlank @Size(max = 128) String merchantName,
        @Size(max = 32) String category,
        @NotBlank @Size(max = 64) String contactName,
        @NotBlank @Size(max = 32) String contactPhone,
        @Size(max = 128) String contactEmail,
        @NotBlank @CreditCode String businessLicenseNo,
        @Size(max = 255) String businessAddress,
        @Pattern(regexp = "T1|D1", message = "结算周期只支持 T1 / D1") String settleCycle) {
}
