-- ============================================================
-- M16 通知与 Webhook 独立服务：数据迁入独立 schema finflow_notify（同实例）
-- 前置：V28 建 webhook 两表、V30/V31 建通知两表（均在主库）；本脚本整体重命名到 finflow_notify
-- 新增 notify_merchant_config：商户通知配置投影（回调地址 / 联系邮箱 / 签名密钥密文），
--   由 backend 的 merchant.notify-config.changed 事件写入，通知服务只读本表，不再同步回查 backend
-- 约定：db/notify 目录下的后续脚本必须显式限定 finflow_notify.，避免落到主库
-- ============================================================
CREATE DATABASE IF NOT EXISTS finflow_notify
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

RENAME TABLE webhook_event TO finflow_notify.webhook_event,
    webhook_delivery TO finflow_notify.webhook_delivery,
    notification_template TO finflow_notify.notification_template,
    notification TO finflow_notify.notification;

CREATE TABLE finflow_notify.notify_merchant_config
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id   BIGINT UNSIGNED NOT NULL COMMENT '商户ID',
    webhook_url   VARCHAR(512)             DEFAULT NULL COMMENT 'Webhook 回调地址（空 = 未订阅）',
    contact_email VARCHAR(128)             DEFAULT NULL COMMENT '联系邮箱（商户通知邮件收件人）',
    secret_cipher VARCHAR(512)             DEFAULT NULL COMMENT 'API 签名密钥 AES-GCM 密文（共享密钥解密）',
    changed_at    DATETIME        NOT NULL COMMENT '配置变更时间（事件携带）',
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '投影更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant (merchant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='商户通知配置投影（事件驱动，仅通知服务读写）';

-- 存量回填：迁移前已存在商户直接投影一份当前配置（最新凭证的密文与商户回调地址/邮箱）。
-- 源表在主库（连接默认 schema），目标表显式限定 finflow_notify，避免数据库名不同时写死。
INSERT INTO finflow_notify.notify_merchant_config (merchant_id, webhook_url, contact_email, secret_cipher, changed_at)
SELECT m.id,
       m.webhook_url,
       m.contact_email,
       (SELECT k.secret_cipher
        FROM merchant_api_key k
        WHERE k.merchant_id = m.id
        ORDER BY k.id DESC
        LIMIT 1),
       NOW()
FROM merchant m;
