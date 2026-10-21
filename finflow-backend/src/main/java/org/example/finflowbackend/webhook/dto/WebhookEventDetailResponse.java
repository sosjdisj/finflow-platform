package org.example.finflowbackend.webhook.dto;

import java.util.List;

/**
 * Webhook 事件详情：报文全文 + 投递日志。
 */
public record WebhookEventDetailResponse(WebhookEventResponse event, String payload,
                                         List<WebhookDeliveryResponse> deliveries) {
}
