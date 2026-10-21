package org.example.finflowbackend.notification.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.notification.entity.Notification;
import org.example.finflowbackend.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内信已读单测（S34）：未读数、单条已读（幂等条件带 read_at IS NULL）、全部已读、通知不存在报错。
 */
class NotificationReadServiceTest {

    private final NotificationMapper notificationMapper = mock(NotificationMapper.class);
    private final NotificationReadService service = new NotificationReadService(notificationMapper);

    @Test
    void unreadCountsNullReadAt() {
        when(notificationMapper.selectCount(any())).thenReturn(7L);
        assertEquals(7L, service.unreadCount());
    }

    @Test
    void markReadSetsReadAtOnlyForUnread() {
        when(notificationMapper.selectById(1L)).thenReturn(new Notification());
        when(notificationMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        service.markRead(1L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<UpdateWrapper<Notification>> captor =
                ArgumentCaptor.forClass((Class) UpdateWrapper.class);
        verify(notificationMapper).update(isNull(), captor.capture());
        UpdateWrapper<Notification> wrapper = captor.getValue();
        // 更新段：只写 read_at；条件段：限定该行且 read_at IS NULL（幂等）
        assertTrue(wrapper.getSqlSet().contains("read_at"));
        String condition = wrapper.getSqlSegment();
        assertTrue(condition.contains("read_at IS NULL"));
        assertTrue(condition.contains("id ="));
    }

    @Test
    void markReadRejectsMissingNotification() {
        when(notificationMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.markRead(9L));
        verify(notificationMapper, never()).update(any(), any());
    }

    @Test
    void markAllReadReturnsMarkedCount() {
        when(notificationMapper.update(any(), any(UpdateWrapper.class))).thenReturn(3);
        assertEquals(3, service.markAllRead());
    }
}
