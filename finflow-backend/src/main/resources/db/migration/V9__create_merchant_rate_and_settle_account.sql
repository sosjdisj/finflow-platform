-- ============================================================
-- S10 商户费率与结算账户
-- 费率：比例费率用万分比整数、固定手续费用分（禁止小数参与计算）
-- 费率历史只增不改；结算账户仅信息管理，不动资金
-- ============================================================
CREATE TABLE merchant_rate
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id BIGINT UNSIGNED NOT NULL COMMENT '商户ID',
    rate_bps    BIGINT                   DEFAULT NULL COMMENT '比例费率（万分比，如 60=0.6%），NULL 不启用',
    fixed_fee   BIGINT                   DEFAULT NULL COMMENT '固定手续费（分），NULL 不启用',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_rate_mid (merchant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='商户费率配置';

CREATE TABLE merchant_rate_log
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id   BIGINT UNSIGNED NOT NULL COMMENT '商户ID',
    old_rate_bps  BIGINT                   DEFAULT NULL COMMENT '改前比例费率（万分比）',
    old_fixed_fee BIGINT                   DEFAULT NULL COMMENT '改前固定手续费（分）',
    new_rate_bps  BIGINT                   DEFAULT NULL COMMENT '改后比例费率（万分比）',
    new_fixed_fee BIGINT                   DEFAULT NULL COMMENT '改后固定手续费（分）',
    operator      VARCHAR(64)     NOT NULL COMMENT '操作人',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_merchant_rate_log_mid (merchant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='商户费率变更历史';

CREATE TABLE merchant_settle_account
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id  BIGINT UNSIGNED NOT NULL COMMENT '商户ID',
    account_type VARCHAR(16)     NOT NULL COMMENT '类型：BANK_CARD/ALIPAY/WECHAT',
    account_name VARCHAR(64)     NOT NULL COMMENT '户名',
    account_no   VARCHAR(64)     NOT NULL COMMENT '账号（查询脱敏返回）',
    bank_name    VARCHAR(64)              DEFAULT NULL COMMENT '开户行（银行卡必填）',
    is_default   TINYINT         NOT NULL DEFAULT 0 COMMENT '默认结算账户：1 是 / 0 否',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_merchant_settle_account_mid (merchant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='商户结算账户';

-- 费率与结算账户操作权限点（id 沿 52 段顺延；页面菜单留给 S11）
INSERT INTO sys_permission (id, code, name, type)
VALUES (52, 'merchant:merchant:rate', '商户费率管理', 2),
       (53, 'merchant:merchant:settle-account', '商户结算账户管理', 2);
