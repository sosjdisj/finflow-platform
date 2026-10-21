package org.example.finflowbackend.payment.event;

import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.common.event.EventDeduplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 示例消费者（打日志验证链路）：演示消费幂等约定——
 * 先按 eventId 去重（Redis），重复投递直接跳过，再处理业务。
 * 账务消费者（阶段 4）、通知消费者（阶段 9）按同样模式接入。
 */
@Component
public class PaymentEventLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventLogConsumer.class);

    static final String GROUP_ID = "finflow-demo-logger";

    private final EventDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    public PaymentEventLogConsumer(EventDeduplicationService deduplicationService, ObjectMapper objectMapper) {
        this.deduplicationService = deduplicationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {PaymentTopics.TOPIC_CREATED, PaymentTopics.TOPIC_SUCCEEDED,
            PaymentTopics.TOPIC_FAILED}, groupId = GROUP_ID)
    public void onMessage(String message) {
        handle(message);
    }

    /** 消费处理入口（包级可见便于单测）：去重 → 打日志。 */
    void handle(String message) {
        DomainEvent event = objectMapper.readValue(message, DomainEvent.class);
        if (!deduplicationService.tryConsume(GROUP_ID, event.eventId())) {
            log.info("[示例消费者] 重复事件跳过: type={}, eventId={}", event.eventType(), event.eventId());
            return;
        }
        log.info("[示例消费者] 收到支付事件: type={}, eventId={}, payload={}",
                event.eventType(), event.eventId(), objectMapper.writeValueAsString(event.payload()));
    }
}
