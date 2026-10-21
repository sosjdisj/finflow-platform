package org.example.finflowbackend.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.finflowbackend.common.validation.CreditCode;

/**
 * 更新商户请求：仅基本信息 / 联系人 / 营业信息 / 结算周期，编号与状态不在其列。
 */
public record MerchantUpdateRequest(
        @NotBlank @Size(max = 128) String merchantName,
        @Size(max = 32) String category,
        @NotBlank @Size(max = 64) String contactName,
        @NotBlank @Size(max = 32) String contactPhone,
        @Size(max = 128) String contactEmail,
        @NotBlank @CreditCode String businessLicenseNo,
        @Size(max = 255) String businessAddress,
        @Pattern(regexp = "T1|D1", message = "结算周期只支持 T1 / D1") String settleCycle,
        @Size(max = 255) String webhookUrl) {
}
