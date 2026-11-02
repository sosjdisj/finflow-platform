package org.example.finflowbackend.notify.projection;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.example.finflowbackend.contract.MerchantNotifyConfigPayload;
import org.example.finflowbackend.notify.entity.NotifyMerchantConfig;
import org.example.finflowbackend.notify.mapper.NotifyMerchantConfigMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知配置投影写入单测（M16）：无投影行时插入、已有行时按商户覆盖，
 * 且清空场景必须走显式 {@code UpdateWrapper.set}（updateById 会忽略 null 导致旧值残留）。
 */
class NotifyConfigProjectionServiceTest {

    private static final LocalDateTime CHANGED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);

    private final NotifyMerchantConfigMapper configMapper = mock(NotifyMerchantConfigMapper.class);
    private final NotifyConfigProjectionService service = new NotifyConfigProjectionService(configMapper);

    @Test
    void insertsProjectionForFirstEvent() {
        when(configMapper.selectOne(any())).thenReturn(null);

        service.upsert(new MerchantNotifyConfigPayload(7L, "https://merchant.example/callback",
                "merchant@example.com", "v1:iv:cipher", CHANGED_AT));

        ArgumentCaptor<NotifyMerchantConfig> captor = ArgumentCaptor.forClass(NotifyMerchantConfig.class);
        verify(configMapper).insert(captor.capture());
        verify(configMapper, never()).update(any(), any());
        NotifyMerchantConfig saved = captor.getValue();
        assertEquals(7L, saved.getMerchantId());
        assertEquals("https://merchant.example/callback", saved.getWebhookUrl());
        assertEquals("merchant@example.com", saved.getContactEmail());
        assertEquals("v1:iv:cipher", saved.getSecretCipher());
        assertEquals(CHANGED_AT, saved.getChangedAt());
    }

    @Test
    void updatesExistingProjectionInPlace() {
        NotifyMerchantConfig existing = new NotifyMerchantConfig();
        existing.setId(10L);
        existing.setMerchantId(7L);
        when(configMapper.selectOne(any())).thenReturn(existing);

        service.upsert(new MerchantNotifyConfigPayload(7L, "https://new.example/callback",
                "new@example.com", "v1:iv:cipher2", CHANGED_AT));

        UpdateWrapper<NotifyMerchantConfig> update = capturedUpdate();
        verify(configMapper, never()).insert(any(NotifyMerchantConfig.class));
        assertTrue(update.getSqlSet().contains("webhook_url"));
        assertTrue(update.getSqlSet().contains("contact_email"));
        assertTrue(update.getSqlSet().contains("secret_cipher"));
        assertTrue(update.getSqlSet().contains("changed_at"));
        assertTrue(update.getParamNameValuePairs().containsValue("https://new.example/callback"));
        assertTrue(update.getParamNameValuePairs().containsValue("new@example.com"));
    }

    @Test
    void clearingMerchantConfigWritesNulls() {
        NotifyMerchantConfig existing = new NotifyMerchantConfig();
        existing.setId(10L);
        existing.setMerchantId(7L);
        when(configMapper.selectOne(any())).thenReturn(existing);

        service.upsert(new MerchantNotifyConfigPayload(7L, null, null, null, CHANGED_AT));

        UpdateWrapper<NotifyMerchantConfig> update = capturedUpdate();
        // 四个字段都是显式 set，null 才会真正写库；若改用 updateById，这些清空操作会被忽略
        assertTrue(update.getSqlSet().contains("webhook_url"));
        assertTrue(update.getSqlSet().contains("contact_email"));
        assertTrue(update.getSqlSet().contains("secret_cipher"));
        assertTrue(update.getParamNameValuePairs().containsValue(CHANGED_AT));
    }

    private UpdateWrapper<NotifyMerchantConfig> capturedUpdate() {
        ArgumentCaptor<UpdateWrapper<NotifyMerchantConfig>> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(configMapper).update(isNull(), captor.capture());
        return captor.getValue();
    }
}
