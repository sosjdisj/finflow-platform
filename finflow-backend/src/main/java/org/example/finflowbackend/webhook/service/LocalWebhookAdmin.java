package org.example.finflowbackend.webhook.service;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.webhook.api.WebhookAdminApi;
import org.example.finflowbackend.webhook.dto.WebhookEventDetailResponse;
import org.example.finflowbackend.webhook.dto.WebhookEventResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 进程内 Webhook 管理实现（M16 回退路径）：默认模式，直接读本进程的 webhook 数据。
 * 通知服务拆出后把 {@code finflow.notify.mode} 切成 remote 即可改走 Feign，代码无需回滚。
 */
@Component
@ConditionalOnProperty(name = "finflow.notify.mode", havingValue = "local", matchIfMissing = true)
public class LocalWebhookAdmin implements WebhookAdminApi {

    private final WebhookEventService eventService;

    public LocalWebhookAdmin(WebhookEventService eventService) {
        this.eventService = eventService;
    }

    @Override
    public PageResponse<WebhookEventResponse> page(long page, long size, Long merchantId,
                                                   String status, String eventType) {
        return eventService.page(page, size, merchantId, status, eventType);
    }

    @Override
    public WebhookEventDetailResponse detail(Long id) {
        return eventService.detail(id);
    }

    @Override
    public WebhookEventResponse manualRetry(Long id) {
        return eventService.manualRetry(id);
    }
}
