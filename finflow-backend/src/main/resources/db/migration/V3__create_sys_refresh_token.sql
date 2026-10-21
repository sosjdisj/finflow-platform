-- ============================================================
-- S04 认证模块：刷新令牌表
-- 只存 SHA-256 哈希，不存明文；吊销 = 写 revoked_at
-- ============================================================
CREATE TABLE sys_refresh_token
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    token_hash CHAR(64)        NOT NULL COMMENT '刷新令牌的 SHA-256 十六进制哈希',
    expires_at DATETIME        NOT NULL COMMENT '过期时间',
    revoked_at DATETIME                 DEFAULT NULL COMMENT '吊销时间，空表示有效',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_refresh_token_hash (token_hash),
    KEY idx_sys_refresh_token_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='刷新令牌';
