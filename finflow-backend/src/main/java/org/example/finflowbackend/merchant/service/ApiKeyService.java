package org.example.finflowbackend.merchant.service;

import org.example.finflowbackend.merchant.api.ApiKeyApi;
import org.example.finflowbackend.merchant.dto.ApiKeyCreateResponse;
import org.example.finflowbackend.merchant.dto.IpWhitelistUpdateRequest;
import org.example.finflowbackend.merchant.dto.MerchantApiKeyResponse;

import java.util.List;

/**
 * 商户 API 凭证：生成、列表、IP 白名单配置。
 * 跨模块只经 {@link ApiKeyApi} 取签名 Secret。
 */
public interface ApiKeyService extends ApiKeyApi {

    /** 生成密钥对；Secret 明文仅本次响应返回，库中存 SHA-256 哈希 + AES-256-GCM 密文。 */
    ApiKeyCreateResponse create(Long merchantId);

    List<MerchantApiKeyResponse> list(Long merchantId);

    MerchantApiKeyResponse updateIpWhitelist(Long merchantId, Long keyId, IpWhitelistUpdateRequest request);
}
