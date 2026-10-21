package org.example.finflowbackend.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 新增/修改风控规则请求。params 为按类型定义的 JSON 串，合法性由对应求值器写入时校验。
 */
public record RuleUpsertRequest(
        @NotBlank @Size(max = 64) String ruleName,
        @NotBlank @Pattern(regexp = "AMOUNT_LIMIT|FREQUENCY|BLACKLIST|SAME_IP_MULTI_ACCOUNT",
                message = "规则类型不合法") String ruleType,
        @NotBlank @Size(max = 1024) String params,
        @NotNull @PositiveOrZero Integer score,
        @NotNull @PositiveOrZero Integer priority,
        @NotNull Boolean enabled,
        @Size(max = 255) String remark) {
}
