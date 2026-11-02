package org.example.finflowbackend.openapi.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 后台维护 OpenAPI 配额请求：0 表示沿用平台默认值；enabled=false 表示该商户该接口停用。
 */
public record OpenApiQuotaUpdateRequest(@NotNull @Min(0) Integer qpsLimit,
                                        @NotNull @Min(0) Long dailyLimit,
                                        @NotNull Boolean enabled) {
}
