package org.example.finflowbackend.notification.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.notification.dto.NotificationResponse;
import org.example.finflowbackend.notification.service.NotificationQueryService;
import org.example.finflowbackend.notification.service.NotificationReadService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知查询与已读接口（S33/S34）。权限编码与 sys_permission 初始化数据一一对应（V30/V31）。
 */
@RestController
@Tag(name = "通知")
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationQueryService queryService;
    private final NotificationReadService readService;

    public NotificationController(NotificationQueryService queryService, NotificationReadService readService) {
        this.queryService = queryService;
        this.readService = readService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('notification:list')")
    public ApiResponse<PageResponse<NotificationResponse>> page(@RequestParam(defaultValue = "1") long page,
                                                                @RequestParam(defaultValue = "20") long size,
                                                                @RequestParam(required = false) Long merchantId,
                                                                @RequestParam(required = false) String templateCode,
                                                                @RequestParam(required = false) String emailStatus) {
        return ApiResponse.ok(queryService.page(page, size, merchantId, templateCode, emailStatus));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('notification:list')")
    public ApiResponse<NotificationResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.detail(id));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('notification:list')")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.ok(readService.unreadCount());
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasAuthority('notification:read')")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        readService.markRead(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAuthority('notification:read')")
    public ApiResponse<Integer> markAllRead() {
        return ApiResponse.ok(readService.markAllRead());
    }
}
