package org.example.finflowbackend.notification.web;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.notification.api.NotificationAdminApi;
import org.example.finflowbackend.notification.dto.NotificationResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知服务内部通知管理端点（M16）：只在通知服务进程注册
 * （{@code finflow.notify.provider=true}）。backend 经 Feign 调用，不对公网 / 运营后台暴露
 * （M13 网关屏蔽 internal 路径）。
 */
@RestController
@RequestMapping("/api/v1/internal/notify/notifications")
@ConditionalOnProperty(name = "finflow.notify.provider", havingValue = "true")
public class NotificationAdminController {

    private final NotificationAdminApi adminApi;

    public NotificationAdminController(NotificationAdminApi adminApi) {
        this.adminApi = adminApi;
    }

    @GetMapping
    public PageResponse<NotificationResponse> page(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size,
                                                   @RequestParam(required = false) Long merchantId,
                                                   @RequestParam(required = false) String templateCode,
                                                   @RequestParam(required = false) String emailStatus) {
        return adminApi.page(page, size, merchantId, templateCode, emailStatus);
    }

    @GetMapping("/{id}")
    public NotificationResponse detail(@PathVariable Long id) {
        return adminApi.detail(id);
    }

    @GetMapping("/unread-count")
    public long unreadCount() {
        return adminApi.unreadCount();
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable Long id) {
        adminApi.markRead(id);
    }

    @PostMapping("/read-all")
    public int markAllRead() {
        return adminApi.markAllRead();
    }
}
