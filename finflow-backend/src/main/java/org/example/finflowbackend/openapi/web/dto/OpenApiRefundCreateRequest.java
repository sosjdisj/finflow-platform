package org.example.finflowbackend.openapi.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * OpenAPI 退款请求：按平台订单号发起，金额单位为分且不得超过剩余可退（服务端行锁内校验）。
 */
public record OpenApiRefundCreateRequest(
        @NotBlank @Size(max = 64) String platformOrderNo,
        @NotNull @Positive Long amount,
        @Size(max = 255) String reason) {
}
