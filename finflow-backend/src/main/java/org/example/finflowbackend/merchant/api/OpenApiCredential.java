package org.example.finflowbackend.merchant.api;

/**
 * OpenAPI AK/SK 验签凭证（M12）：只给接入层做验签用，不含哈希等内部字段。
 * secret 为解密后的明文，仅存活于验签组件内存，不落日志、不出接口。
 */
public record OpenApiCredential(Long merchantId, String apiKey, String secret, String ipWhitelist) {
}
