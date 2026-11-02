package org.example.finflowbackend.webhook.remote;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.webhook.api.WebhookAdminApi;
import org.example.finflowbackend.webhook.dto.WebhookEventDetailResponse;
import org.example.finflowbackend.webhook.dto.WebhookEventResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 远程 Webhook 管理客户端（M16）：由 {@link WebhookAdminRemoteConfig} 在
 * {@code finflow.notify.mode=remote} 时注册；服务名为通知服务在 Nacos 的注册名。
 * 降级由 {@link WebhookAdminFallbackFactory} 提供（快速失败，不让管理页无限等待）。
 */
@FeignClient(name = "finflow-notify", contextId = "webhookAdmin",
        path = "/api/v1/internal/notify/webhooks",
        configuration = WebhookAdminFeignConfig.class,
        fallbackFactory = WebhookAdminFallbackFactory.class)
public interface WebhookAdminClient extends WebhookAdminApi {

    @Override
    @GetMapping("/events")
    PageResponse<WebhookEventResponse> page(@RequestParam("page") long page,
                                            @RequestParam("size") long size,
                                            @RequestParam(value = "merchantId", required = false) Long merchantId,
                                            @RequestParam(value = "status", required = false) String status,
                                            @RequestParam(value = "eventType", required = false) String eventType);

    @Override
    @GetMapping("/events/{id}")
    WebhookEventDetailResponse detail(@PathVariable("id") Long id);

    @Override
    @PostMapping("/events/{id}/retry")
    WebhookEventResponse manualRetry(@PathVariable("id") Long id);
}
