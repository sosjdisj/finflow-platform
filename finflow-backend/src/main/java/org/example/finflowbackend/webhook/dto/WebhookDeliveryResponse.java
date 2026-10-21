package org.example.finflowbackend.webhook.dto;

import org.example.finflowbackend.webhook.entity.WebhookDelivery;

import java.time.LocalDateTime;

/**
 * 投递日志条目。
 */
public record WebhookDeliveryResponse(Long id, Integer attemptNo, Boolean success, Integer responseCode,
                                      String responseBody, Integer durationMs, LocalDateTime createdAt) {

    public static WebhookDeliveryResponse from(WebhookDelivery delivery) {
        return new WebhookDeliveryResponse(delivery.getId(), delivery.getAttemptNo(), delivery.getSuccess(),
                delivery.getResponseCode(), delivery.getResponseBody(), delivery.getDurationMs(),
                delivery.getCreatedAt());
    }
}
