package org.example.finflowbackend.webhook.dto;

import org.example.finflowbackend.webhook.entity.WebhookEvent;

import java.time.LocalDateTime;

/**
 * Webhook 事件列表/详情条目（payload 不在列表返回，详情单独给）。
 */
public record WebhookEventResponse(Long id, Long merchantId, String eventId, String eventType,
                                   String bizOrderNo, String status, Integer retryCount,
                                   LocalDateTime nextRetryAt, LocalDateTime createdAt) {

    public static WebhookEventResponse from(WebhookEvent event) {
        return new WebhookEventResponse(event.getId(), event.getMerchantId(), event.getEventId(),
                event.getEventType(), event.getBizOrderNo(), event.getStatus(), event.getRetryCount(),
                event.getNextRetryAt(), event.getCreatedAt());
    }
}
