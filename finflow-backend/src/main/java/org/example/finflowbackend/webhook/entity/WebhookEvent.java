package org.example.finflowbackend.webhook.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Webhook 事件登记（表 webhook_event）。对应迁移脚本 V28__create_webhook_tables.sql。
 * 一行 = 一个待投递事件；payload 存最终投递报文全文（投出的与库里存的一致）。
 * 状态由 WebhookRetryPolicy 统一推进：PENDING → SUCCESS / EXHAUSTED。
 */
@Getter
@Setter
@TableName("webhook_event")
public class WebhookEvent {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_EXHAUSTED = "EXHAUSTED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    /** Kafka 事件 ID（DomainEvent.eventId），唯一索引防重复入库。 */
    private String eventId;

    private String eventType;

    /** 关联业务单号（支付平台单号 / 退款单号）。 */
    private String bizOrderNo;

    /** 投递报文全文（{eventId,eventType,occurredAt,data}）。 */
    private String payload;

    private String status;

    /** 已重试次数（不含首次投递）。 */
    private Integer retryCount;

    /** 下次重试时间（PENDING 时非空）。 */
    private LocalDateTime nextRetryAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
