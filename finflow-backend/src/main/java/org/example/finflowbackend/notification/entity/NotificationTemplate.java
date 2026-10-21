package org.example.finflowbackend.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 通知模板（表 notification_template）。对应迁移脚本 V30__create_notification_tables.sql。
 * {{key}} 占位符，站内信与邮件共用；模板编码同事件类型。
 */
@Getter
@Setter
@TableName("notification_template")
public class NotificationTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String templateCode;

    private String title;

    private String body;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
