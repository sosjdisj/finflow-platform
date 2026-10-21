-- ============================================================
-- S18 账户与余额（一张表）：主体类型 + 主体ID + 币种 唯一
-- 余额一律用分（bigint）；version 供 MP @Version 乐观锁使用
-- 资金变更只能经 AccountService 统一入口，行锁 + 乐观锁双保险
-- ============================================================
CREATE TABLE account
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    owner_type        VARCHAR(16)     NOT NULL COMMENT '主体类型：MERCHANT/PLATFORM/CHANNEL',
    owner_id          BIGINT UNSIGNED NOT NULL COMMENT '主体ID（平台账户固定为0）',
    currency          CHAR(3)         NOT NULL DEFAULT 'CNY' COMMENT '币种，ISO 4217',
    available_balance BIGINT          NOT NULL DEFAULT 0 COMMENT '可用余额（分）',
    frozen_balance    BIGINT          NOT NULL DEFAULT 0 COMMENT '冻结余额（分）',
    version           INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_currency (owner_type, owner_id, currency)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='账户与余额';
