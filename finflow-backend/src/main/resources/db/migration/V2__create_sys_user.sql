-- ============================================================
-- S04 认证模块：系统用户表
-- 密码只存 BCrypt 哈希，禁止存明文或可逆密文
-- ============================================================
CREATE TABLE sys_user
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(64)     NOT NULL COMMENT '登录名',
    password_hash VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密码哈希',
    nickname      VARCHAR(64)     NOT NULL COMMENT '显示名',
    email         VARCHAR(128)             DEFAULT NULL COMMENT '邮箱',
    phone         VARCHAR(32)              DEFAULT NULL COMMENT '手机号',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 禁用',
    last_login_at DATETIME                 DEFAULT NULL COMMENT '最近登录时间',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='系统用户';
