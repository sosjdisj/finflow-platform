package org.example.finflowbackend.webhook.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.webhook.dto.WebhookEventDetailResponse;
import org.example.finflowbackend.webhook.dto.WebhookEventResponse;
import org.example.finflowbackend.webhook.service.WebhookEventService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook 事件管理接口（S32）。权限编码与 sys_permission 初始化数据一一对应（V28）。
 */
@RestController
@Tag(name = "Webhook 事件")
@RequestMapping("/api/v1/webhooks/events")
public class WebhookEventController {

    private final WebhookEventService eventService;

    public WebhookEventController(WebhookEventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('webhook:event:list')")
    public ApiResponse<PageResponse<WebhookEventResponse>> page(@RequestParam(defaultValue = "1") long page,
                                                                @RequestParam(defaultValue = "20") long size,
                                                                @RequestParam(required = false) Long merchantId,
                                                                @RequestParam(required = false) String status,
                                                                @RequestParam(required = false) String eventType) {
        return ApiResponse.ok(eventService.page(page, size, merchantId, status, eventType));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('webhook:event:list')")
    public ApiResponse<WebhookEventDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(eventService.detail(id));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('webhook:event:retry')")
    public ApiResponse<WebhookEventResponse> retry(@PathVariable Long id) {
        return ApiResponse.ok(eventService.manualRetry(id));
    }
}
