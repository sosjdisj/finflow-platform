package org.example.finflowbackend.merchant.service.impl;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.common.util.HmacSigner;
import org.example.finflowbackend.merchant.MerchantErrorCode;
import org.example.finflowbackend.merchant.dto.ApiKeyCreateResponse;
import org.example.finflowbackend.merchant.dto.IpWhitelistUpdateRequest;
import org.example.finflowbackend.merchant.dto.MerchantDetailResponse;
import org.example.finflowbackend.merchant.entity.MerchantApiKey;
import org.example.finflowbackend.merchant.mapper.MerchantApiKeyMapper;
import org.example.finflowbackend.merchant.service.MerchantService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * API 凭证单测：Secret 存哈希+密文、明文仅创建返回一次、白名单全量覆盖、跨商户凭证隔离、
 * 签名 Secret 解密取回（S32）。
 */
class ApiKeyServiceImplTest {

    private static final String TEST_KEY = "finflow-test-encrypt-key";

    private final MerchantApiKeyMapper apiKeyMapper = mock(MerchantApiKeyMapper.class);
    private final MerchantService merchantService = mock(MerchantService.class);
    private final ApiKeyServiceImpl service = new ApiKeyServiceImpl(apiKeyMapper, merchantService, TEST_KEY);

    @Test
    void createStoresHashOnlyAndReturnsSecretOnce() {
        ApiKeyCreateResponse response = service.create(1L);

        assertTrue(response.secret().startsWith("fs_") && response.secret().length() == 67);
        assertTrue(response.apiKey().startsWith("fk_"));
        ArgumentCaptor<MerchantApiKey> captor = ArgumentCaptor.forClass(MerchantApiKey.class);
        verify(apiKeyMapper).insert(captor.capture());
        // 库里存的是哈希与密文，不是明文
        assertEquals(HmacSigner.sha256Hex(response.secret()), captor.getValue().getSecretHash());
        assertNotEquals(response.secret(), captor.getValue().getSecretHash());
        assertNotEquals(response.secret(), captor.getValue().getSecretCipher());
    }

    @Test
    void createRejectsUnknownMerchant() {
        when(merchantService.getDetail(9L)).thenThrow(new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(9L));

        assertEquals(MerchantErrorCode.MERCHANT_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void listDelegatesToMerchantExistence() {
        when(merchantService.getDetail(1L)).thenReturn(new MerchantDetailResponse(null, List.of()));
        when(apiKeyMapper.selectList(any())).thenReturn(List.of(apiKey(5L, 1L, null)));

        var result = service.list(1L);

        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).id());
    }

    @Test
    void updateIpWhitelistOverwritesFully() {
        when(apiKeyMapper.selectOne(any())).thenReturn(apiKey(5L, 1L, "1.1.1.1"));

        var response = service.updateIpWhitelist(1L, 5L, new IpWhitelistUpdateRequest(List.of("10.0.0.1", "10.0.0.0/24")));

        assertEquals("10.0.0.1,10.0.0.0/24", response.ipWhitelist());
        // 实际落库值（含置 null 清空语义）由 HTTP 验收覆盖，这里只确认走的是 UpdateWrapper 更新
        verify(apiKeyMapper).update(isNull(), any());
    }

    @Test
    void updateIpWhitelistWithEmptyListClearsIt() {
        when(apiKeyMapper.selectOne(any())).thenReturn(apiKey(5L, 1L, "1.1.1.1"));

        var response = service.updateIpWhitelist(1L, 5L, new IpWhitelistUpdateRequest(List.of()));

        assertNull(response.ipWhitelist());
    }

    @Test
    void updateIpWhitelistRejectsKeyOfOtherMerchant() {
        when(apiKeyMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateIpWhitelist(2L, 5L, new IpWhitelistUpdateRequest(List.of("1.1.1.1"))));

        assertEquals(MerchantErrorCode.API_KEY_NOT_FOUND, error.getErrorCode());
    }

    private MerchantApiKey apiKey(Long id, Long merchantId, String ipWhitelist) {
        MerchantApiKey apiKey = new MerchantApiKey();
        apiKey.setId(id);
        apiKey.setMerchantId(merchantId);
        apiKey.setApiKey("fk_test");
        apiKey.setSecretHash("hash");
        apiKey.setIpWhitelist(ipWhitelist);
        return apiKey;
    }

    @Test
    void findSigningSecretDecryptsLatestKey() {
        String secret = "fs_" + "a".repeat(64);
        MerchantApiKey latest = apiKey(7L, 1L, null);
        latest.setSecretCipher(org.example.finflowbackend.common.util.AesCipher.encrypt(secret, TEST_KEY));
        when(apiKeyMapper.selectOne(any())).thenReturn(latest);

        var found = service.findSigningSecret(1L);

        assertTrue(found.isPresent());
        assertEquals(secret, found.get());
    }

    @Test
    void findSigningSecretEmptyWhenLegacyKeyHasNoCipher() {
        // S32 之前创建的历史凭证无密文：返回 empty，由调用方决定跳过投递
        when(apiKeyMapper.selectOne(any())).thenReturn(apiKey(6L, 1L, null));

        assertTrue(service.findSigningSecret(1L).isEmpty());
    }

    @Test
    void findSigningSecretEmptyWhenNoKeyAtAll() {
        when(apiKeyMapper.selectOne(any())).thenReturn(null);

        assertTrue(service.findSigningSecret(1L).isEmpty());
    }
}
