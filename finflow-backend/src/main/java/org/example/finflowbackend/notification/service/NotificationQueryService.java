package org.example.finflowbackend.notification.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.notification.dto.NotificationResponse;
import org.example.finflowbackend.notification.entity.Notification;
import org.example.finflowbackend.notification.mapper.NotificationMapper;
import org.springframework.stereotype.Service;

/**
 * 通知查询（S33）：分页与详情，供管理端查看发送记录。
 */
@Service
public class NotificationQueryService {

    private final NotificationMapper notificationMapper;

    public NotificationQueryService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    public PageResponse<NotificationResponse> page(long page, long size, Long merchantId,
                                                   String templateCode, String emailStatus) {
        Page<Notification> result = notificationMapper.selectPage(new Page<>(page, size),
                Wrappers.<Notification>query()
                        .eq(merchantId != null, "receiver_id", merchantId)
                        .eq(templateCode != null && !templateCode.isBlank(), "template_code", templateCode)
                        .eq(emailStatus != null && !emailStatus.isBlank(), "email_status", emailStatus)
                        .orderByDesc("id"));
        return PageResponse.of(result.convert(NotificationResponse::from));
    }

    public NotificationResponse detail(Long id) {
        return NotificationResponse.from(notificationMapper.selectById(id));
    }
}
