package org.example.finflowbackend.webhook.web;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.webhook.api.WebhookAdminApi;
import org.example.finflowbackend.webhook.dto.WebhookEventDetailResponse;
import org.example.finflowbackend.webhook.dto.WebhookEventResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知服务内部 Webhook 管理端点（M16）：只在通知服务进程注册
 * （{@code finflow.notify.provider=true}）。backend 经 Feign 调用，不对公网 / 运营后台暴露
 * （M13 网关屏蔽 internal 路径）。
 */
@RestController
@RequestMapping("/api/v1/internal/notify/webhooks")
@ConditionalOnProperty(name = "finflow.notify.provider", havingValue = "true")
public class WebhookAdminController {

    private final WebhookAdminApi adminApi;

    public WebhookAdminController(WebhookAdminApi adminApi) {
        this.adminApi = adminApi;
    }

    @GetMapping("/events")
    public PageResponse<WebhookEventResponse> page(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size,
                                                   @RequestParam(required = false) Long merchantId,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String eventType) {
        return adminApi.page(page, size, merchantId, status, eventType);
    }

    @GetMapping("/events/{id}")
    public WebhookEventDetailResponse detail(@PathVariable Long id) {
        return adminApi.detail(id);
    }

    @PostMapping("/events/{id}/retry")
    public WebhookEventResponse retry(@PathVariable Long id) {
        return adminApi.manualRetry(id);
    }
}
