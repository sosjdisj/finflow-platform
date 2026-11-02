package org.example.finflowbackend.notification.api;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.notification.dto.NotificationResponse;

/**
 * 通知记录管理能力（M16 进程边界）：列表 / 详情 / 未读数 / 已读。
 *
 * <p>管理端只依赖本接口：{@code finflow.notify.mode=local} 时由 backend 进程内实现，
 * {@code remote} 时由 Feign 调通知服务。内部端点与前台接口共用同一契约。</p>
 */
public interface NotificationAdminApi {

    PageResponse<NotificationResponse> page(long page, long size, Long merchantId,
                                            String templateCode, String emailStatus);

    NotificationResponse detail(Long id);

    long unreadCount();

    void markRead(Long id);

    int markAllRead();
}
