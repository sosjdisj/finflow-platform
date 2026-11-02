package org.example.finflowbackend.notify.event;

import org.example.finflowbackend.contract.DomainEvent;
import org.example.finflowbackend.contract.EventTopics;
import org.example.finflowbackend.contract.MerchantNotifyConfigPayload;
import org.example.finflowbackend.notify.projection.NotifyConfigProjectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 商户通知配置事件消费者（M16）：仅通知服务进程消费（{@code finflow.notify.provider=true}），
 * 把商户回调地址 / 联系邮箱 / 密钥密文落到自己的投影表。
 * 这是 webhook 与通知唯一的商户配置来源，保证「不引入任何同步调用」。
 */
@Component
@ConditionalOnProperty(name = "finflow.notify.provider", havingValue = "true")
public class NotifyConfigEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotifyConfigEventConsumer.class);

    static final String GROUP_ID = "finflow-notify-config";

    private final ObjectMapper objectMapper;
    private final NotifyConfigProjectionService projectionService;

    public NotifyConfigEventConsumer(ObjectMapper objectMapper, NotifyConfigProjectionService projectionService) {
        this.objectMapper = objectMapper;
        this.projectionService = projectionService;
    }

    @KafkaListener(topics = EventTopics.MERCHANT_NOTIFY_CONFIG, groupId = GROUP_ID)
    public void onNotifyConfigChanged(String message) {
        DomainEvent event = objectMapper.readValue(message, DomainEvent.class);
        MerchantNotifyConfigPayload payload =
                objectMapper.convertValue(event.payload(), MerchantNotifyConfigPayload.class);
        projectionService.upsert(payload);
    }
}
