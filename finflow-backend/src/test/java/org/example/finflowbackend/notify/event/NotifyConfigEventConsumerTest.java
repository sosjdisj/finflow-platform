package org.example.finflowbackend.notify.event;

import org.example.finflowbackend.contract.MerchantNotifyConfigPayload;
import org.example.finflowbackend.notify.projection.NotifyConfigProjectionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 通知配置事件消费者单测（M16）：线上报文解析为 payload 后交给投影服务，
 * 商户清空回调地址 / 邮箱时 null 必须原样透传（否则通知服务会继续投递到旧地址）。
 */
class NotifyConfigEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotifyConfigProjectionService projectionService = mock(NotifyConfigProjectionService.class);
    private final NotifyConfigEventConsumer consumer =
            new NotifyConfigEventConsumer(objectMapper, projectionService);

    @Test
    void parsesEventAndProjectionReceivesFullSnapshot() {
        consumer.onNotifyConfigChanged("""
                {"eventId":"evt-ncfg-1","eventType":"MERCHANT_NOTIFY_CONFIG_CHANGED",
                 "occurredAt":"2026-11-01T02:00:00Z","version":1,
                 "payload":{"merchantId":7,"webhookUrl":"https://merchant.example/callback",
                            "contactEmail":"merchant@example.com","secretCipher":"v1:iv:cipher",
                            "changedAt":"2026-11-01T10:00:00"}}
                """);

        MerchantNotifyConfigPayload payload = capturedPayload();
        assertEquals(7L, payload.merchantId());
        assertEquals("https://merchant.example/callback", payload.webhookUrl());
        assertEquals("merchant@example.com", payload.contactEmail());
        assertEquals("v1:iv:cipher", payload.secretCipher());
        assertEquals(LocalDateTime.of(2026, 11, 1, 10, 0), payload.changedAt());
    }

    @Test
    void missingConfigFieldsPropagateAsNull() {
        consumer.onNotifyConfigChanged("""
                {"eventId":"evt-ncfg-2","eventType":"MERCHANT_NOTIFY_CONFIG_CHANGED",
                 "occurredAt":"2026-11-01T02:00:00Z","version":1,
                 "payload":{"merchantId":7,"changedAt":"2026-11-01T10:00:00"}}
                """);

        MerchantNotifyConfigPayload payload = capturedPayload();
        assertEquals(7L, payload.merchantId());
        assertNull(payload.webhookUrl());
        assertNull(payload.contactEmail());
        assertNull(payload.secretCipher());
    }

    private MerchantNotifyConfigPayload capturedPayload() {
        ArgumentCaptor<MerchantNotifyConfigPayload> captor =
                ArgumentCaptor.forClass(MerchantNotifyConfigPayload.class);
        verify(projectionService).upsert(captor.capture());
        return captor.getValue();
    }
}
