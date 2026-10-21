package org.example.finflowbackend.webhook.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Webhook 投递日志（表 webhook_delivery）。一次投递尝试一行，只增不改（流水性质）。
 */
@Getter
@Setter
@TableName("webhook_delivery")
public class WebhookDelivery {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** webhook_event.id。 */
    private Long eventRowId;

    /** 第几次投递（1 = 首次投递，2+ 为重试）。 */
    private Integer attemptNo;

    private Boolean success;

    /** 商户响应 HTTP 状态码（连接失败为 null）。 */
    private Integer responseCode;

    /** 商户响应体（截断 512 字符）。 */
    private String responseBody;

    /** 本次投递耗时（毫秒）。 */
    private Integer durationMs;

    private LocalDateTime createdAt;
}
