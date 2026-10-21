-- ============================================================
-- S06 登录安全：登录日志表 + 刷新令牌补设备信息
-- 登录日志为审计表：只增不改，不使用逻辑删除/自动填充
-- ============================================================
CREATE TABLE sys_login_log
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(64)     NOT NULL COMMENT '尝试登录的用户名（无论用户是否存在）',
    user_id     BIGINT UNSIGNED DEFAULT NULL COMMENT '登录成功时的用户 ID，失败为空',
    success     TINYINT         NOT NULL COMMENT '1 成功 0 失败',
    fail_reason VARCHAR(128)    DEFAULT NULL COMMENT '失败原因，成功为空',
    ip          VARCHAR(45)     DEFAULT NULL COMMENT '客户端 IP（兼容 IPv6）',
    user_agent  VARCHAR(255)    DEFAULT NULL COMMENT '客户端 User-Agent',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sys_login_log_user (user_id),
    KEY idx_sys_login_log_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='登录日志';

-- 设备列表复用 refresh token：记录签发时的设备名与 IP，不建独立设备表
ALTER TABLE sys_refresh_token
    ADD COLUMN device_name VARCHAR(128) DEFAULT NULL COMMENT '登录设备名（User-Agent 截断）' AFTER token_hash,
    ADD COLUMN ip          VARCHAR(45)  DEFAULT NULL COMMENT '签发时客户端 IP' AFTER device_name;
