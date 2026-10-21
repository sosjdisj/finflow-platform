-- ============================================================
-- S33 通知中心：
-- 1) notification_template 模板表：5 类事件模板（{{key}} 占位符，发送时渲染）
--    其中风控拦截 / 对账异常暂无 Kafka 事件源（S30/S29 未发布），模板预置待接入
-- 2) notification 通知记录表：一行 = 一次事件通知（渲染后内容），站内信落库即送达；
--    uk(event_id) 防 Kafka 重复投递重复入库（与 Redis 消费去重双保险）
-- 渠道：站内信 + Email；Email 降级策略（SMTP 未配置只记日志）读应用配置，不落库
-- 收件人模型：商户事件（支付/退款/结算）→ MERCHANT（邮件发 contact_email）；
--             平台事件（风控拦截/对账异常）→ USER（sys_user.email）
-- 3) 通知查询权限点（id 沿 90 段顺延）
-- ============================================================

CREATE TABLE notification_template
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    template_code VARCHAR(64)     NOT NULL COMMENT '模板编码（同事件类型）PAYMENT_SUCCEEDED/REFUND_SUCCEEDED/SETTLEMENT_SUCCEEDED/RISK_INTERCEPTED/RECONCILIATION_DIFFERENCE',
    title         VARCHAR(128)    NOT NULL COMMENT '标题模板（支持 {{key}} 占位符）',
    body          VARCHAR(1024)   NOT NULL COMMENT '正文模板（支持 {{key}} 占位符）',
    enabled       TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用 1启用/0停用',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_code (template_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='通知模板（占位符渲染，站内信与邮件共用）';

CREATE TABLE notification
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    template_code VARCHAR(64)     NOT NULL COMMENT '模板编码（同事件类型）',
    event_id      VARCHAR(64)     NOT NULL COMMENT 'Kafka 事件ID（DomainEvent.eventId），唯一索引防重复入库',
    receiver_type VARCHAR(20)     NOT NULL COMMENT '收件人类型 MERCHANT商户/USER平台用户',
    receiver_id   BIGINT UNSIGNED NOT NULL COMMENT '收件人ID（merchant_id / sys_user.id）',
    title         VARCHAR(128)    NOT NULL COMMENT '渲染后标题',
    content       VARCHAR(1024)   NOT NULL COMMENT '渲染后正文',
    in_app_status VARCHAR(20)     NOT NULL DEFAULT 'SENT' COMMENT '站内信渠道状态（落库即送达）SENT',
    email_status  VARCHAR(20)              DEFAULT NULL COMMENT '邮件渠道状态 SENT/SKIPPED未配置SMTP/FAILED发送失败',
    email_error   VARCHAR(512)             DEFAULT NULL COMMENT '邮件失败原因',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_receiver (receiver_type, receiver_id, created_at),
    KEY idx_template (template_code, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='通知记录（站内信落库即送达；邮件状态单列记录）';

INSERT INTO notification_template (template_code, title, body) VALUES
('PAYMENT_SUCCEEDED',
 '支付成功：订单 {{platformOrderNo}}',
 '您的订单 {{platformOrderNo}} 已支付成功，金额 {{amount}} {{currency}}，支付时间 {{paidAt}}。'),
('REFUND_SUCCEEDED',
 '退款成功：退款单 {{refundNo}}',
 '您的退款单 {{refundNo}}（原订单 {{platformOrderNo}}）已退款成功，金额 {{refundAmount}} {{currency}}，退款时间 {{refundedAt}}。'),
('SETTLEMENT_SUCCEEDED',
 '结算完成：结算单 {{settleNo}}',
 '您 {{settleDate}} 的结算单 {{settleNo}} 已完成打款，金额 {{totalAmount}} {{currency}}，执行时间 {{executedAt}}。'),
('RISK_INTERCEPTED',
 '风控拦截：订单 {{platformOrderNo}}',
 '订单 {{platformOrderNo}} 被风控{{action}}，风险分 {{score}}，原因：{{reasons}}。'),
('RECONCILIATION_DIFFERENCE',
 '对账差异：任务 {{taskNo}}',
 '对账任务 {{taskNo}} 发现 {{differenceType}} 差异，平台金额 {{platformAmount}}，渠道金额 {{channelAmount}}，请及时处理。');

INSERT INTO sys_permission (id, code, name, type)
VALUES (99, 'notification:list', '通知查询', 2);
