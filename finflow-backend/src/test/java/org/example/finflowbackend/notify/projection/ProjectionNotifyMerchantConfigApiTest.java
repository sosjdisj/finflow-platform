package org.example.finflowbackend.notify.projection;

import org.example.finflowbackend.common.util.AesCipher;
import org.example.finflowbackend.notify.api.NotifyConfig;
import org.example.finflowbackend.notify.entity.NotifyMerchantConfig;
import org.example.finflowbackend.notify.mapper.NotifyMerchantConfigMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 通知服务进程内配置读取单测（M16）：只读投影表，密钥密文用共享密钥解密；
 * 无投影返回 empty（webhook 据此判为未订阅），历史凭证无密文时签名密钥为 null。
 */
class ProjectionNotifyMerchantConfigApiTest {

    private static final String KEY = "test-secret-encrypt-key";

    private final NotifyMerchantConfigMapper configMapper = mock(NotifyMerchantConfigMapper.class);
    private final ProjectionNotifyMerchantConfigApi api =
            new ProjectionNotifyMerchantConfigApi(configMapper, KEY);

    @Test
    void decryptsSigningSecretFromProjection() {
        NotifyMerchantConfig config = new NotifyMerchantConfig();
        config.setWebhookUrl("https://merchant.example/callback");
        config.setContactEmail("merchant@example.com");
        config.setSecretCipher(AesCipher.encrypt("fs_test_secret", KEY));
        when(configMapper.selectOne(any())).thenReturn(config);

        NotifyConfig result = api.find(7L).orElseThrow();

        assertEquals("https://merchant.example/callback", result.webhookUrl());
        assertEquals("merchant@example.com", result.contactEmail());
        assertEquals("fs_test_secret", result.signingSecret());
    }

    @Test
    void missingProjectionReturnsEmpty() {
        when(configMapper.selectOne(any())).thenReturn(null);

        assertTrue(api.find(7L).isEmpty());
    }

    @Test
    void legacyCredentialWithoutCipherKeepsSecretNull() {
        NotifyMerchantConfig config = new NotifyMerchantConfig();
        config.setWebhookUrl("https://merchant.example/callback");
        config.setContactEmail("merchant@example.com");
        when(configMapper.selectOne(any())).thenReturn(config);

        NotifyConfig result = api.find(7L).orElseThrow();

        assertNull(result.signingSecret());
    }
}
