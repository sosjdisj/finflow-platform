package org.example.finflowbackend.common.event;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.example.finflowbackend.common.trace.TracePropagation;
import org.example.finflowbackend.contract.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/**
 * 领域事件发布器：统一 Kafka 出口。
 * 事务内调用时注册 afterCommit 回调，保证事务提交后才发布（避免消费端读到未提交数据产生脏事件）；
 * 无事务时直接发布。事件以 JSON 字符串传输，key 用业务 ID 保证同实体事件分区有序。
 */
@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TracePropagation tracePropagation;

    public DomainEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                TracePropagation tracePropagation) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.tracePropagation = tracePropagation;
    }

    /** 事务提交后发布事件（无事务则立即发布）。 */
    public void publish(String topic, String key, DomainEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 事务内调用时注册 afterCommit 回调，保证事务提交后才发布（避免消费端读到未提交数据产生脏事件）；
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(topic, key, event);
                }
            });
            return;
        }
        send(topic, key, event);
    }

    private void send(String topic, String key, DomainEvent event) {
        String json = objectMapper.writeValueAsString(event);
        // M15：把当前 span 上下文注入消息头，消费端据此恢复同一个 traceId
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, json);
        tracePropagation.inject(record.headers());
        kafkaTemplate.send(record);
        log.info("领域事件已发布: topic={}, key={}, eventType={}, eventId={}",
                topic, key, event.eventType(), event.eventId());
    }
}
