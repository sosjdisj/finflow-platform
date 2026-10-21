-- ============================================================
-- S09 商户审核与 API 凭证
-- Secret 只在创建时显示一次，库存 SHA-256 哈希（与 refresh token 同法）
-- IP 白名单跟凭证走（全量覆盖更新）；Webhook 回调地址跟商户走（阶段 9 投递用）
-- ============================================================
CREATE TABLE merchant_api_key
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id  BIGINT UNSIGNED NOT NULL COMMENT '商户ID',
    api_key      VARCHAR(64)     NOT NULL COMMENT 'API Key（明文存储，fk_ 前缀）',
    secret_hash  CHAR(64)        NOT NULL COMMENT 'Secret 的 SHA-256 哈希（fs_ 前缀，明文仅创建时返回一次）',
    ip_whitelist VARCHAR(512)             DEFAULT NULL COMMENT 'IP/CIDR 白名单，逗号分隔，全量覆盖更新',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_key (api_key),
    KEY idx_merchant_api_key_mid (merchant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='商户 API 凭证';

ALTER TABLE merchant
    ADD COLUMN webhook_url VARCHAR(255) DEFAULT NULL COMMENT 'Webhook 回调地址（阶段 9 投递时使用）' AFTER settle_cycle;

-- 商户审核与凭证操作权限点（id 沿 50 段顺延；页面菜单留给 S11）
INSERT INTO sys_permission (id, code, name, type)
VALUES (50, 'merchant:merchant:review', '商户审核', 2),
       (51, 'merchant:merchant:api-key', '商户凭证管理', 2);
