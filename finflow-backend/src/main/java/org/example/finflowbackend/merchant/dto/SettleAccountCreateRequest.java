package org.example.finflowbackend.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 结算账户新增请求：银行卡开户行必填（service 校验）；首个账户自动设为默认。
 */
public record SettleAccountCreateRequest(
        @NotBlank @Pattern(regexp = "BANK_CARD|ALIPAY|WECHAT", message = "账户类型不合法") String accountType,
        @NotBlank @Size(max = 64) String accountName,
        @NotBlank @Size(max = 64) String accountNo,
        @Size(max = 64) String bankName) {
}
