package org.example.finflowgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 边缘验签窗口（M13/M14）：前缀与 backend 的 {@code OpenApiProperties} 相同（finflow.openapi.*），
 * 因此共享 dataId finflow-common.yaml 里的同一份值同时喂给网关与 backend，不会出现两边窗口不一致。
 * 只声明本进程用到的两个键，backend 侧的其他 openapi 键由 Spring Boot 的宽松绑定忽略。
 * 经启动类 @ConfigurationPropertiesScan 注册。
 */
@ConfigurationProperties(prefix = "finflow.openapi")
public record OpenApiEdgeProperties(int timestampWindowSeconds, int nonceTtlSeconds) {

    public OpenApiEdgeProperties {
        if (timestampWindowSeconds <= 0) {
            timestampWindowSeconds = 300;
        }
        if (nonceTtlSeconds <= 0) {
            nonceTtlSeconds = 600;
        }
    }
}
