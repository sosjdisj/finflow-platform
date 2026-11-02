package org.example.finflowbackend.openapi.quota;

/**
 * OpenAPI 接口编码（配额维度的最小粒度）：与 merchant_openapi_quota.api_code 一一对应。
 * 新增接口时先加编码，再在配额表 / 管理接口里配置阈值。
 */
public enum OpenApiApiCode {

    PAYMENT_CREATE,
    PAYMENT_QUERY,
    PAYMENT_CLOSE,
    REFUND_CREATE,
    REFUND_QUERY;

    /** 解析接口编码；未知编码返回 null，由调用方按参数错误处理。 */
    public static OpenApiApiCode of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (OpenApiApiCode value : values()) {
            if (value.name().equalsIgnoreCase(code.trim())) {
                return value;
            }
        }
        return null;
    }
}
