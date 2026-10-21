package org.example.finflowbackend.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 通知记录（表 notification）。一行 = 一次事件通知（渲染后内容）。
 * 站内信落库即送达（in_app_status=SENT）；邮件渠道状态单列记录（SENT/SKIPPED/FAILED）。
 */
@Getter
@Setter
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String templateCode;

    /** Kafka 事件 ID，唯一索引防重复入库。 */
    private String eventId;

    /** 收件人类型 MERCHANT / USER。 */
    private String receiverType;

    private Long receiverId;

    private String title;

    private String content;

    /** 站内信已读时间（NULL=未读，管理端标记，S34）。 */
    private LocalDateTime readAt;

    private String inAppStatus;

    /** 邮件渠道状态 SENT / SKIPPED（未配置 SMTP）/ FAILED。 */
    private String emailStatus;

    private String emailError;

    private LocalDateTime createdAt;
}
