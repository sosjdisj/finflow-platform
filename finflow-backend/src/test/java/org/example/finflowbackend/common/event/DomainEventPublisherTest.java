package org.example.finflowbackend.common.event;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.example.finflowbackend.common.trace.TracePropagation;
import org.example.finflowbackend.contract.DomainEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * M15：领域事件出口把 traceparent 注入 Kafka 消息头；事务内仍然只在提交后发送。
 */
class DomainEventPublisherTest {

    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final TracePropagation tracePropagation = mock(TracePropagation.class);
    private final DomainEventPublisher publisher =
            new DomainEventPublisher(kafkaTemplate, objectMapper, tracePropagation);

    @Test
    void sendsRecordWithTraceHeaders() {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventType\":\"payment.succeeded\"}");

        publisher.publish("payment.succeeded", "P001",
                DomainEvent.of("payment.succeeded", Map.of("paymentId", "P001")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertEquals("payment.succeeded", record.topic());
        assertEquals("P001", record.key());
        assertEquals("{\"eventType\":\"payment.succeeded\"}", record.value());
        verify(tracePropagation).inject(record.headers());
    }

    @Test
    void defersSendUntilAfterCommitInsideTransaction() {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publish("payment.succeeded", "P001", DomainEvent.of("payment.succeeded", Map.of()));
            verifyNoInteractions(kafkaTemplate);

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(kafkaTemplate).send(any(ProducerRecord.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
