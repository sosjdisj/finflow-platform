package org.example.finflowgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * M13/M14 网关配置（finflow.gateway.*）。
 * 后端地址默认走服务发现 {@code lb://finflow-backend}；本地无注册中心时用环境变量 FINFLOW_BACKEND_URI 回退固定地址。
 * 验签窗口与 nonce 存活期在 {@link OpenApiEdgeProperties}（与 backend 同键同值），不在本类重复声明。
 */
@ConfigurationProperties(prefix = "finflow.gateway")
public record GatewayProperties(String backendUri, String internalToken, int profileCacheSeconds,
                                int profileTimeoutMillis) {

    /** 服务发现名：与 backend 的 spring.application.name 一致。 */
    public static final String DEFAULT_BACKEND_URI = "lb://finflow-backend";

    public GatewayProperties {
        backendUri = blankToDefault(backendUri, DEFAULT_BACKEND_URI);
        internalToken = blankToDefault(internalToken, "finflow-internal-dev-token");
        profileCacheSeconds = positiveOr(profileCacheSeconds, 15);
        profileTimeoutMillis = positiveOr(profileTimeoutMillis, 2_000);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int positiveOr(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
