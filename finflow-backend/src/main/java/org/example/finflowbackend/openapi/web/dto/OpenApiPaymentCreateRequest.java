package org.example.finflowbackend.openapi.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * OpenAPI 下单请求：merchantId 不在请求体内，由 AK/SK 凭证决定，商户无法伪造。
 * 金额单位一律为分；字段语义与后台建单一致（共用 payment 内部编排）。
 */
public record OpenApiPaymentCreateRequest(
        @NotBlank @Size(max = 64) String merchantOrderNo,
        @NotNull @Positive Long amount,
        @Pattern(regexp = "[A-Z]{3}", message = "币种须为 3 位大写 ISO 4217 代码") String currency,
        @NotBlank @Size(max = 128) String subject,
        @Size(max = 255) String body,
        @NotBlank @Size(max = 32) String paymentMethod,
        @Size(max = 512) String callbackUrl,
        LocalDateTime expiredAt,
        @Size(max = 64) String clientIp,
        @Size(max = 128) String deviceId) {
}
