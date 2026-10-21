package org.example.finflowbackend.merchant.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.common.util.AesCipher;
import org.example.finflowbackend.common.util.HmacSigner;
import org.example.finflowbackend.merchant.MerchantErrorCode;
import org.example.finflowbackend.merchant.dto.ApiKeyCreateResponse;
import org.example.finflowbackend.merchant.dto.IpWhitelistUpdateRequest;
import org.example.finflowbackend.merchant.dto.MerchantApiKeyResponse;
import org.example.finflowbackend.merchant.entity.MerchantApiKey;
import org.example.finflowbackend.merchant.mapper.MerchantApiKeyMapper;
import org.example.finflowbackend.merchant.service.ApiKeyService;
import org.example.finflowbackend.merchant.service.MerchantService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

/**
 * API 凭证实现。
 * Secret 生成规则：fs_ + 64 位十六进制（32 字节随机）；库中存 SHA-256 哈希，
 * S32 起同时存 AES-256-GCM 密文（Webhook 签名解密用），明文仅创建响应返回一次。
 */
@Service
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MerchantApiKeyMapper apiKeyMapper;
    private final MerchantService merchantService;
    private final String secretEncryptKey;

    public ApiKeyServiceImpl(MerchantApiKeyMapper apiKeyMapper,
                             MerchantService merchantService,
                             @Value("${finflow.security.secret-encrypt-key}") String secretEncryptKey) {
        this.apiKeyMapper = apiKeyMapper;
        this.merchantService = merchantService;
        this.secretEncryptKey = secretEncryptKey;
    }

    @Override
    public ApiKeyCreateResponse create(Long merchantId) {
        merchantService.getDetail(merchantId);
        String secret = "fs_" + randomHex(32);
        MerchantApiKey apiKey = new MerchantApiKey();
        apiKey.setMerchantId(merchantId);
        apiKey.setApiKey("fk_" + randomHex(16));
        apiKey.setSecretHash(HmacSigner.sha256Hex(secret));
        apiKey.setSecretCipher(AesCipher.encrypt(secret, secretEncryptKey));
        apiKeyMapper.insert(apiKey);
        return new ApiKeyCreateResponse(apiKey.getId(), merchantId, apiKey.getApiKey(), secret, apiKey.getCreatedAt());
    }

    @Override
    public List<MerchantApiKeyResponse> list(Long merchantId) {
        merchantService.getDetail(merchantId);
        return apiKeyMapper.selectList(Wrappers.<MerchantApiKey>query()
                        .eq("merchant_id", merchantId).orderByDesc("id"))
                .stream().map(MerchantApiKeyResponse::from).toList();
    }

    @Override
    public MerchantApiKeyResponse updateIpWhitelist(Long merchantId, Long keyId, IpWhitelistUpdateRequest request) {
        MerchantApiKey apiKey = requireApiKey(merchantId, keyId);
        List<String> ips = request.ips() == null ? List.of() : request.ips();
        // 全量覆盖；空列表 = 清空（updateById 忽略 null 字段，须用 UpdateWrapper 显式置 null）
        String joined = ips.isEmpty() ? null : String.join(",", ips);
        apiKeyMapper.update(null, Wrappers.<MerchantApiKey>update()
                .eq("id", apiKey.getId())
                .set("ip_whitelist", joined));
        apiKey.setIpWhitelist(joined);
        return MerchantApiKeyResponse.from(apiKey);
    }

    private MerchantApiKey requireApiKey(Long merchantId, Long keyId) {
        MerchantApiKey apiKey = apiKeyMapper.selectOne(Wrappers.<MerchantApiKey>query()
                .eq("id", keyId).eq("merchant_id", merchantId));
        if (apiKey == null) {
            throw new BusinessException(MerchantErrorCode.API_KEY_NOT_FOUND);
        }
        return apiKey;
    }

    @Override
    public Optional<String> findSigningSecret(Long merchantId) {
        MerchantApiKey latest = apiKeyMapper.selectOne(Wrappers.<MerchantApiKey>query()
                .eq("merchant_id", merchantId).orderByDesc("id").last("LIMIT 1"));
        if (latest == null || latest.getSecretCipher() == null) {
            return Optional.empty();
        }
        return Optional.of(AesCipher.decrypt(latest.getSecretCipher(), secretEncryptKey));
    }

    private String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return HmacSigner.hex(buf);
    }
}
