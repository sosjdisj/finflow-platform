package org.example.finflowbackend.notification.service;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.notification.api.NotificationAdminApi;
import org.example.finflowbackend.notification.dto.NotificationResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 进程内通知管理实现（M16 回退路径）：默认模式，直接读本进程的通知数据。
 * 通知服务拆出后把 {@code finflow.notify.mode} 切成 remote 即可改走 Feign，代码无需回滚。
 */
@Component
@ConditionalOnProperty(name = "finflow.notify.mode", havingValue = "local", matchIfMissing = true)
public class LocalNotificationAdmin implements NotificationAdminApi {

    private final NotificationQueryService queryService;
    private final NotificationReadService readService;

    public LocalNotificationAdmin(NotificationQueryService queryService, NotificationReadService readService) {
        this.queryService = queryService;
        this.readService = readService;
    }

    @Override
    public PageResponse<NotificationResponse> page(long page, long size, Long merchantId,
                                                   String templateCode, String emailStatus) {
        return queryService.page(page, size, merchantId, templateCode, emailStatus);
    }

    @Override
    public NotificationResponse detail(Long id) {
        return queryService.detail(id);
    }

    @Override
    public long unreadCount() {
        return readService.unreadCount();
    }

    @Override
    public void markRead(Long id) {
        readService.markRead(id);
    }

    @Override
    public int markAllRead() {
        return readService.markAllRead();
    }
}
