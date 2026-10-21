package org.example.finflowbackend.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.notification.entity.Notification;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
