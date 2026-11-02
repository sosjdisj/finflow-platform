package org.example.finflowbackend.openapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAPI 接入安全参数（finflow.openapi.*）：验签窗口与 nonce 存活期。
 * 配额默认值属于限流阈值，M14 起由 {@code TunableConfig} 从配置中心热读。
 * 经启动类 @ConfigurationPropertiesScan 注册。
 */
@ConfigurationProperties(prefix = "finflow.openapi")
public record OpenApiProperties(int timestampWindowSeconds, int nonceTtlSeconds) {

    public OpenApiProperties {
        if (timestampWindowSeconds <= 0) {
            timestampWindowSeconds = 300;
        }
        if (nonceTtlSeconds <= 0) {
            nonceTtlSeconds = 600;
        }
    }
}
