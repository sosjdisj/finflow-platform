package org.example.finflowbackend.merchant.event;

import org.example.finflowbackend.common.event.DomainEventPublisher;
import org.example.finflowbackend.contract.DomainEvent;
import org.example.finflowbackend.contract.EventTopics;
import org.example.finflowbackend.contract.MerchantNotifyConfigPayload;
import org.example.finflowbackend.merchant.entity.Merchant;
import org.example.finflowbackend.merchant.entity.MerchantApiKey;
import org.example.finflowbackend.merchant.mapper.MerchantApiKeyMapper;
import org.example.finflowbackend.merchant.mapper.MerchantMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商户通知配置事件发布单测（M16）：商户资料或凭证变更后发布配置快照，
 * 事件里只允许出现密钥密文（明文声明绝不外发），无凭证时密文为空。
 */
class MerchantNotifyConfigPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MerchantMapper merchantMapper = mock(MerchantMapper.class);
    private final MerchantApiKeyMapper apiKeyMapper = mock(MerchantApiKeyMapper.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
    private final MerchantNotifyConfigPublisher publisher =
            new MerchantNotifyConfigPublisher(merchantMapper, apiKeyMapper, eventPublisher);

    @Test
    void publishesSnapshotWithSecretCipherOnly() {
        Merchant merchant = merchant(7L, "https://merchant.example/callback", "merchant@example.com");
        when(merchantMapper.selectById(7L)).thenReturn(merchant);
        when(apiKeyMapper.selectOne(any())).thenReturn(apiKey("v1:iv:cipher"));

        publisher.publish(7L);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(eq(EventTopics.MERCHANT_NOTIFY_CONFIG), eq("7"), captor.capture());
        MerchantNotifyConfigPayload payload = objectMapper.convertValue(
                captor.getValue().payload(), MerchantNotifyConfigPayload.class);
        assertEquals(7L, payload.merchantId());
        assertEquals("https://merchant.example/callback", payload.webhookUrl());
        assertEquals("merchant@example.com", payload.contactEmail());
        assertEquals("v1:iv:cipher", payload.secretCipher());
    }

    @Test
    void publishesNullCipherWhenMerchantHasNoCredential() {
        when(merchantMapper.selectById(7L)).thenReturn(merchant(7L, "https://merchant.example/callback", null));
        when(apiKeyMapper.selectOne(any())).thenReturn(null);

        publisher.publish(7L);

        MerchantNotifyConfigPayload payload = capturedPayload();
        assertNull(payload.secretCipher());
        assertNull(payload.contactEmail());
    }

    @Test
    void skipsPublishWhenMerchantMissing() {
        when(merchantMapper.selectById(7L)).thenReturn(null);

        publisher.publish(7L);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    private MerchantNotifyConfigPayload capturedPayload() {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(any(), any(), captor.capture());
        return objectMapper.convertValue(captor.getValue().payload(), MerchantNotifyConfigPayload.class);
    }

    private Merchant merchant(Long id, String webhookUrl, String contactEmail) {
        Merchant merchant = new Merchant();
        merchant.setId(id);
        merchant.setWebhookUrl(webhookUrl);
        merchant.setContactEmail(contactEmail);
        return merchant;
    }

    private MerchantApiKey apiKey(String secretCipher) {
        MerchantApiKey key = new MerchantApiKey();
        key.setMerchantId(7L);
        key.setSecretCipher(secretCipher);
        return key;
    }
}
