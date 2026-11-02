package org.example.finflowbackend.notification.remote;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.notification.api.NotificationAdminApi;
import org.example.finflowbackend.notification.dto.NotificationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 远程通知管理客户端（M16）：由 {@link NotificationAdminRemoteConfig} 在
 * {@code finflow.notify.mode=remote} 时注册；服务名为通知服务在 Nacos 的注册名。
 * 降级由 {@link NotificationAdminFallbackFactory} 提供（快速失败，不让管理页无限等待）。
 */
@FeignClient(name = "finflow-notify", contextId = "notificationAdmin",
        path = "/api/v1/internal/notify/notifications",
        configuration = NotificationAdminFeignConfig.class,
        fallbackFactory = NotificationAdminFallbackFactory.class)
public interface NotificationAdminClient extends NotificationAdminApi {

    @Override
    @GetMapping
    PageResponse<NotificationResponse> page(@RequestParam("page") long page,
                                            @RequestParam("size") long size,
                                            @RequestParam(value = "merchantId", required = false) Long merchantId,
                                            @RequestParam(value = "templateCode", required = false) String templateCode,
                                            @RequestParam(value = "emailStatus", required = false) String emailStatus);

    @Override
    @GetMapping("/{id}")
    NotificationResponse detail(@PathVariable("id") Long id);

    @Override
    @GetMapping("/unread-count")
    long unreadCount();

    @Override
    @PostMapping("/{id}/read")
    void markRead(@PathVariable("id") Long id);

    @Override
    @PostMapping("/read-all")
    int markAllRead();
}
