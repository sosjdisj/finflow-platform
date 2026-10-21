-- ============================================================
-- S19 资金流水（只增不改）：无 updated_at、无逻辑删除，代码层只提供 insert 与查询
-- amount 带符号（正=入账，负=出账）；余额变更必须与流水写入同一事务
-- 资金流约定（第一版简化）：支付成功 → 平台备付金账户 + 订单全额；
-- 手续费在清分阶段只算不动账，结算阶段执行时 备付金 - 商户应得、商户账户 + 商户应得，
-- 手续费收入届时记平台收入户
-- ============================================================
CREATE TABLE account_ledger
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    account_id     BIGINT UNSIGNED NOT NULL COMMENT '账户ID',
    biz_type       VARCHAR(32)     NOT NULL COMMENT '业务类型：PAYMENT/SETTLEMENT/ADJUSTMENT',
    biz_no         VARCHAR(64)     NOT NULL COMMENT '业务单号（如平台订单号）',
    amount         BIGINT          NOT NULL COMMENT '变动金额（分，带符号：正=入账，负=出账）',
    balance_before BIGINT          NOT NULL COMMENT '变动前可用余额（分）',
    balance_after  BIGINT          NOT NULL COMMENT '变动后可用余额（分）',
    currency       CHAR(3)         NOT NULL DEFAULT 'CNY' COMMENT '币种，ISO 4217',
    source         VARCHAR(32)     NOT NULL COMMENT '来源：PAYMENT_EVENT/CONSOLE/TASK',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    -- 消费幂等的库层兜底：同业务同账户只允许一条流水
    UNIQUE KEY uk_biz (biz_type, biz_no, account_id),
    KEY idx_ledger_account (account_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='资金流水（只增不改）';

-- 流水一致性校验接口权限点（id 沿 56 顺延；资金中心菜单留给 S20）
INSERT INTO sys_permission (id, code, name, type)
VALUES (57, 'ledger:consistency:query', '资金流水一致性校验', 2);
