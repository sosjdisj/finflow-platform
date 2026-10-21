package org.example.finflowbackend.merchant.service;

import org.example.finflowbackend.merchant.dto.ApiKeyCreateResponse;
import org.example.finflowbackend.merchant.dto.IpWhitelistUpdateRequest;
import org.example.finflowbackend.merchant.dto.MerchantApiKeyResponse;

import java.util.List;
import java.util.Optional;

/**
 * 商户 API 凭证：生成、列表、IP 白名单配置。
 */
public interface ApiKeyService {

    /** 生成密钥对；Secret 明文仅本次响应返回，库中存 SHA-256 哈希 + AES-256-GCM 密文。 */
    ApiKeyCreateResponse create(Long merchantId);

    List<MerchantApiKeyResponse> list(Long merchantId);

    MerchantApiKeyResponse updateIpWhitelist(Long merchantId, Long keyId, IpWhitelistUpdateRequest request);

    /**
     * 取商户最新凭证的签名 Secret（解密后明文），供 Webhook 投递签名（S32）。
     * 历史凭证无密文时返回 empty（商户重新生成凭证即可）。
     */
    Optional<String> findSigningSecret(Long merchantId);
}
