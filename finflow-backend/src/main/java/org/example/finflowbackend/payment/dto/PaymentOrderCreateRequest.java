package org.example.finflowbackend.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 创建支付订单请求。金额单位为分；过期时间不传默认 2 小时后。
 * clientIp / deviceId 为风控要素（S30），可选透传，不落订单表。
 */
public record PaymentOrderCreateRequest(
        @NotNull @Positive Long merchantId,
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
