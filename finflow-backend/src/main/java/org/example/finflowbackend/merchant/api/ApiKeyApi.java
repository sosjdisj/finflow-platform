package org.example.finflowbackend.merchant.api;

import java.util.Optional;

/**
 * merchant API 凭证对外能力（M03 facade）：签名 Secret 查询。
 * 凭证生成与 IP 白名单维护留在模块内部服务接口。
 */
public interface ApiKeyApi {

    /**
     * 取商户最新凭证的签名 Secret（解密后明文），供 Webhook 投递签名（S32）。
     * 历史凭证无密文时返回 empty（商户重新生成凭证即可）。
     */
    Optional<String> findSigningSecret(Long merchantId);

    /**
     * 按 API Key 取 OpenAPI 验签凭证（M12）。
     * 凭证不存在、已停用或没有密文（历史凭证）时返回 empty，调用方统一按「凭证无效」处理。
     */
    Optional<OpenApiCredential> findCredential(String apiKey);
}
