-- ============================================================
-- S32 Webhook 投递与重试：
-- 1) webhook_event 事件登记表：一行 = 一个待投递事件（payload 存最终投递报文全文，
--    投出去的与库里存的一致，排查无歧义）；uk(event_id) 防 Kafka 重复投递重复入库
-- 2) webhook_delivery 投递日志表：一行 = 一次投递尝试（请求头/响应码/耗时），只增不改
-- 3) Webhook 管理权限点（投递日志查询 + 手动重试；页面菜单留给 S34）
-- 重试参数（最大重试次数）读应用配置 finflow.webhook，不落库
-- ============================================================

CREATE TABLE webhook_event
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id   BIGINT UNSIGNED NOT NULL COMMENT '商户ID',
    event_id      VARCHAR(64)     NOT NULL COMMENT 'Kafka 事件ID（DomainEvent.eventId），唯一索引防重复入库',
    event_type    VARCHAR(64)     NOT NULL COMMENT '事件类型 PAYMENT_CREATED/PAYMENT_SUCCEEDED/PAYMENT_FAILED/REFUND_SUCCEEDED',
    biz_order_no  VARCHAR(64)     NOT NULL COMMENT '关联业务单号（支付平台单号/退款单号）',
    payload       JSON            NOT NULL COMMENT '投递报文全文（{eventId,eventType,occurredAt,data}）',
    status        VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING待投递或待重试/SUCCESS投递成功/EXHAUSTED重试耗尽',
    retry_count   INT             NOT NULL DEFAULT 0 COMMENT '已重试次数（不含首次投递）',
    next_retry_at DATETIME                 DEFAULT NULL COMMENT '下次重试时间（PENDING 时非空）',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_merchant (merchant_id, created_at),
    KEY idx_retry_scan (status, next_retry_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Webhook 事件登记（消费业务事件生成，投递成功/耗尽为终态）';

CREATE TABLE webhook_delivery
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_row_id  BIGINT UNSIGNED NOT NULL COMMENT 'webhook_event.id',
    attempt_no    INT             NOT NULL COMMENT '第几次投递（1=首次投递，2+ 为重试）',
    success       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '本次投递是否成功 1成功/0失败',
    response_code INT                      DEFAULT NULL COMMENT '商户响应 HTTP 状态码（连接失败为 NULL）',
    response_body VARCHAR(512)             DEFAULT NULL COMMENT '商户响应体（截断 512 字符）',
    duration_ms   INT                      DEFAULT NULL COMMENT '本次投递耗时（毫秒）',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投递时间',
    PRIMARY KEY (id),
    KEY idx_event_row (event_row_id, attempt_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Webhook 投递日志（一次尝试一行，只增不改）';

-- Webhook 管理权限点（id 沿 90 段顺延）
INSERT INTO sys_permission (id, code, name, type)
VALUES (97, 'webhook:event:list', 'Webhook 投递日志查询', 2),
       (98, 'webhook:event:retry', 'Webhook 手动重试', 2);
