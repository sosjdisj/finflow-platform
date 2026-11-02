package org.example.finflowbackend.webhook.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.finflowbackend.common.event.EventDeduplicationService;
import org.example.finflowbackend.contract.DomainEvent;
import org.example.finflowbackend.contract.EventTopics;
import org.example.finflowbackend.contract.PaymentCreatedPayload;
import org.example.finflowbackend.contract.PaymentFailedPayload;
import org.example.finflowbackend.contract.PaymentSucceededPayload;
import org.example.finflowbackend.contract.RefundSucceededPayload;
import org.example.finflowbackend.notify.api.NotifyConfig;
import org.example.finflowbackend.notify.api.NotifyMerchantConfigApi;
import org.example.finflowbackend.webhook.service.WebhookDeliveryService;
import org.example.finflowbackend.webhook.service.WebhookEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Webhook 事件消费者（S32 / M16）：订阅支付 / 退款事件，登记为待投递事件并立即首投，
 * 失败由 WebhookRetryTask 按指数退避重试。
 *
 * <p>M16 起彻底去同步依赖：商户归属与业务单号全部取自事件 payload，
 * 回调地址 / 签名密钥取自 notify 配置投影（{@link NotifyMerchantConfigApi}），
 * 不再回查 payment / merchant / api-key 模块。因此外部回调再慢也只在通知服务进程内阻塞。</p>
 *
 * <p>幂等：Redis 按 eventId 去重在前，webhook_event.uk(event_id) 兜底在后。
 * 商户未配置回调地址或无可签名凭证（历史凭证无密文）时不登记——无订阅即无投递。</p>
 */
@Component
@ConditionalOnProperty(name = "finflow.notify.worker", havingValue = "true", matchIfMissing = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventConsumer.class);

    /** 消费组（去重 key 前缀）。 */
    static final String GROUP_ID = "finflow-webhook";

    private final EventDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;
    private final NotifyMerchantConfigApi configApi;
    private final WebhookEventService eventService;
    private final WebhookDeliveryService deliveryService;

    public WebhookEventConsumer(EventDeduplicationService deduplicationService,
                                ObjectMapper objectMapper,
                                NotifyMerchantConfigApi configApi,
                                WebhookEventService eventService,
                                WebhookDeliveryService deliveryService) {
        this.deduplicationService = deduplicationService;
        this.objectMapper = objectMapper;
        this.configApi = configApi;
        this.eventService = eventService;
        this.deliveryService = deliveryService;
    }

    @KafkaListener(topics = EventTopics.PAYMENT_CREATED, groupId = GROUP_ID)
    public void onPaymentCreated(String message) {
        handle(message, event -> {
            PaymentCreatedPayload payload = objectMapper.convertValue(event.payload(), PaymentCreatedPayload.class);
            return new Target(payload.merchantId(), payload.platformOrderNo());
        });
    }

    @KafkaListener(topics = EventTopics.PAYMENT_SUCCEEDED, groupId = GROUP_ID)
    public void onPaymentSucceeded(String message) {
        handle(message, event -> {
            PaymentSucceededPayload payload = objectMapper.convertValue(event.payload(), PaymentSucceededPayload.class);
            return new Target(payload.merchantId(), payload.platformOrderNo());
        });
    }

    @KafkaListener(topics = EventTopics.PAYMENT_FAILED, groupId = GROUP_ID)
    public void onPaymentFailed(String message) {
        handle(message, event -> {
            PaymentFailedPayload payload = objectMapper.convertValue(event.payload(), PaymentFailedPayload.class);
            return new Target(payload.merchantId(), payload.platformOrderNo());
        });
    }

    @KafkaListener(topics = EventTopics.REFUND_SUCCEEDED, groupId = GROUP_ID)
    public void onRefundSucceeded(String message) {
        handle(message, event -> {
            RefundSucceededPayload payload = objectMapper.convertValue(event.payload(), RefundSucceededPayload.class);
            return new Target(payload.merchantId(), payload.refundNo());
        });
    }

    /** 事件处理入口（公开便于测试直调）：去重 → 定位商户 → 登记 → 首投。 */
    public void handle(String message, TargetResolver resolver) {
        DomainEvent event = objectMapper.readValue(message, DomainEvent.class);
        if (!deduplicationService.tryConsume(GROUP_ID, event.eventId())) {
            log.info("[Webhook消费者] 重复事件跳过: eventId={}", event.eventId());
            return;
        }
        Target target = resolver.resolve(event);
        if (!subscribable(target.merchantId())) {
            return;
        }
        Long eventRowId = eventService.enqueue(target.merchantId(), event.eventId(), event.eventType(),
                target.bizOrderNo(), buildPayload(event));
        if (eventRowId == null) {
            return;
        }
        boolean delivered = deliveryService.deliver(eventRowId);
        log.info("[Webhook消费者] 首投完成: eventId={}, delivered={}", event.eventId(), delivered);
    }

    /** 商户配置了回调地址且存在可签名凭证才登记（无订阅即无投递）。 */
    private boolean subscribable(Long merchantId) {
        NotifyConfig config = configApi.find(merchantId).orElse(null);
        if (config == null || config.webhookUrl() == null || config.webhookUrl().isBlank()) {
            log.info("[Webhook消费者] 商户未配置回调地址，跳过登记: merchantId={}", merchantId);
            return false;
        }
        if (config.signingSecret() == null) {
            log.warn("[Webhook消费者] 商户凭证无签名密文（历史凭证），跳过登记，请重新生成凭证: merchantId={}",
                    merchantId);
            return false;
        }
        return true;
    }

    /** 组最终投递报文：{eventId, eventType, occurredAt, data}，与库中 payload 完全一致。 */
    private String buildPayload(DomainEvent event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.eventId());
        envelope.put("eventType", event.eventType());
        envelope.put("occurredAt", event.occurredAt().toString());
        envelope.put("data", event.payload());
        return objectMapper.writeValueAsString(envelope);
    }

    /** 回调目标：商户 + 业务单号。 */
    record Target(Long merchantId, String bizOrderNo) {
    }

    @FunctionalInterface
    public interface TargetResolver {
        Target resolve(DomainEvent event);
    }
}
