package org.example.finflowbackend.payment.event;

import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.common.event.EventDeduplicationService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 示例消费者单测：首投处理、重复投递跳过（消费幂等约定的可测形态）。
 */
class PaymentEventLogConsumerTest {

    private final EventDeduplicationService deduplicationService = mock(EventDeduplicationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PaymentEventLogConsumer consumer =
            new PaymentEventLogConsumer(deduplicationService, objectMapper);

    @Test
    void firstDeliveryIsHandled() {
        when(deduplicationService.tryConsume(eq(PaymentEventLogConsumer.GROUP_ID), eq("evt-1"))).thenReturn(true);

        consumer.handle(eventJson("evt-1"));

        verify(deduplicationService).tryConsume(PaymentEventLogConsumer.GROUP_ID, "evt-1");
    }

    @Test
    void duplicateDeliveryIsSkipped() {
        when(deduplicationService.tryConsume(eq(PaymentEventLogConsumer.GROUP_ID), eq("evt-2"))).thenReturn(false);

        // 去重命中：handle 正常返回（跳过业务处理），且每个事件只查一次去重
        consumer.handle(eventJson("evt-2"));

        verify(deduplicationService).tryConsume(PaymentEventLogConsumer.GROUP_ID, "evt-2");
    }

    private String eventJson(String eventId) {
        return objectMapper.writeValueAsString(new DomainEvent(eventId, PaymentTopics.TYPE_SUCCEEDED,
                java.time.Instant.now(),
                new PaymentSucceededPayload(1L, "P20261017000001", 10000L, "CNY", null)));
    }
}
