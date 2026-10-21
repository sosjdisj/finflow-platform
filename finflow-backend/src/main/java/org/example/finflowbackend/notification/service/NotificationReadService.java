package org.example.finflowbackend.notification.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.notification.NotificationErrorCode;
import org.example.finflowbackend.notification.entity.Notification;
import org.example.finflowbackend.notification.mapper.NotificationMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 站内信已读（S34）：报告要求「铃铛 + 已读」，S33 只落发送记录，此处按最小支撑补齐。
 * 管理端统一已读（运营处理视角）：read_at 为 NULL 即未读；标记动作幂等（已读不再更新）。
 */
@Service
public class NotificationReadService {

    private final NotificationMapper notificationMapper;

    public NotificationReadService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /** 未读数（铃铛角标）。 */
    public long unreadCount() {
        return notificationMapper.selectCount(Wrappers.<Notification>query().isNull("read_at"));
    }

    /** 标记单条已读；已读时静默跳过（幂等）。 */
    public void markRead(Long id) {
        requireExists(id);
        notificationMapper.update(null, Wrappers.<Notification>update()
                .eq("id", id)
                .isNull("read_at")
                .set("read_at", LocalDateTime.now()));
    }

    /** 全部标记已读，返回本次标记条数。 */
    public int markAllRead() {
        return notificationMapper.update(null, Wrappers.<Notification>update()
                .isNull("read_at")
                .set("read_at", LocalDateTime.now()));
    }

    private void requireExists(Long id) {
        if (notificationMapper.selectById(id) == null) {
            throw new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }
}
