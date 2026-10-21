package org.example.finflowbackend.webhook.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.common.event.EventDeduplicationService;
import org.example.finflowbackend.merchant.service.ApiKeyService;
import org.example.finflowbackend.merchant.service.MerchantService;
import org.example.finflowbackend.payment.dto.PaymentOrderResponse;
import org.example.finflowbackend.payment.event.PaymentTopics;
import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.example.finflowbackend.refund.event.RefundTopics;
import org.example.finflowbackend.webhook.service.WebhookDeliveryService;
import org.example.finflowbackend.webhook.service.WebhookEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Webhook 事件消费者（S32）：订阅支付 / 退款事件，登记为待投递事件并立即首投，
 * 失败由 WebhookRetryTask 按指数退避重试。
 * 幂等：Redis 按 eventId 去重在前，webhook_event.uk(event_id) 兜底在后。
 * 事实源：支付事件以回查订单为准（防 payload 陈旧，沿清分消费者惯例）；
 * 退款事件 payload 自带商户与退款单号，直接使用。
 * 商户未配置回调地址或无可签名凭证（历史凭证无密文）时不登记——无订阅即无投递。
 */
@Component
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventConsumer.class);

    /** 消费组（去重 key 前缀）。 */
    static final String GROUP_ID = "finflow-webhook";

    private final EventDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;
    private final PaymentOrderService paymentOrderService;
    private final MerchantService merchantService;
    private final ApiKeyService apiKeyService;
    private final WebhookEventService eventService;
    private final WebhookDeliveryService deliveryService;

    public WebhookEventConsumer(EventDeduplicationService deduplicationService,
                                ObjectMapper objectMapper,
                                PaymentOrderService paymentOrderService,
                                MerchantService merchantService,
                                ApiKeyService apiKeyService,
                                WebhookEventService eventService,
                                WebhookDeliveryService deliveryService) {
        this.deduplicationService = deduplicationService;
        this.objectMapper = objectMapper;
        this.paymentOrderService = paymentOrderService;
        this.merchantService = merchantService;
        this.apiKeyService = apiKeyService;
        this.eventService = eventService;
        this.deliveryService = deliveryService;
    }

    @KafkaListener(topics = PaymentTopics.TOPIC_CREATED, groupId = GROUP_ID)
    public void onPaymentCreated(String message) {
        handle(message, event -> {
            PaymentOrderResponse order = paymentOrderService.get(orderId(event));
            return new Target(order.merchantId(), order.platformOrderNo());
        });
    }

    @KafkaListener(topics = PaymentTopics.TOPIC_SUCCEEDED, groupId = GROUP_ID)
    public void onPaymentSucceeded(String message) {
        handle(message, event -> {
            PaymentOrderResponse order = paymentOrderService.get(orderId(event));
            return new Target(order.merchantId(), order.platformOrderNo());
        });
    }

    @KafkaListener(topics = PaymentTopics.TOPIC_FAILED, groupId = GROUP_ID)
    public void onPaymentFailed(String message) {
        handle(message, event -> {
            PaymentOrderResponse order = paymentOrderService.get(orderId(event));
            return new Target(order.merchantId(), order.platformOrderNo());
        });
    }

    @KafkaListener(topics = RefundTopics.TOPIC_REFUND_SUCCEEDED, groupId = GROUP_ID)
    public void onRefundSucceeded(String message) {
        handle(message, event -> {
            RefundPayload payload = objectMapper.convertValue(event.payload(), RefundPayload.class);
            return new Target(payload.merchantId, payload.refundNo);
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
        var merchant = merchantService.getDetail(merchantId).merchant();
        if (merchant == null || merchant.webhookUrl() == null) {
            log.info("[Webhook消费者] 商户未配置回调地址，跳过登记: merchantId={}", merchantId);
            return false;
        }
        if (apiKeyService.findSigningSecret(merchantId).isEmpty()) {
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

    private Long orderId(DomainEvent event) {
        return objectMapper.convertValue(event.payload(), OrderPayload.class).orderId;
    }

    /** 回调目标：商户 + 业务单号。 */
    record Target(Long merchantId, String bizOrderNo) {
    }

    @FunctionalInterface
    public interface TargetResolver {
        Target resolve(DomainEvent event);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderPayload(Long orderId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RefundPayload(Long orderId, String refundNo, Long merchantId) {
    }
}
