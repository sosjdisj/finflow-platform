-- ============================================================
-- S25 结算审核与执行：
-- 1) settlement_order 扩审核与执行字段
-- 2) settlement_order_state_log 状态留痕（只增不改，沿退款留痕表模式）
-- 3) 操作权限点（菜单与页面归 S26）
-- ============================================================

-- 审核与执行字段
ALTER TABLE settlement_order
    ADD COLUMN approved_at DATETIME DEFAULT NULL COMMENT '审核通过时间' AFTER calculated_at,
    ADD COLUMN approved_by VARCHAR(64) DEFAULT NULL COMMENT '审核人（登录名）' AFTER approved_at,
    ADD COLUMN executed_at DATETIME DEFAULT NULL COMMENT '执行完成（入账）时间' AFTER approved_by,
    ADD COLUMN fail_reason VARCHAR(255) DEFAULT NULL COMMENT '最近一次执行失败原因' AFTER executed_at;

-- 状态变更留痕（铁律 2 配套：谁、何时、from、to、原因）
CREATE TABLE settlement_order_state_log
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    settlement_id BIGINT UNSIGNED NOT NULL COMMENT '结算单ID（settlement_order.id）',
    from_status VARCHAR(16)     NOT NULL COMMENT '变更前状态',
    to_status   VARCHAR(16)     NOT NULL COMMENT '变更后状态',
    reason      VARCHAR(255)             DEFAULT NULL COMMENT '变更原因',
    operator    VARCHAR(64)              DEFAULT NULL COMMENT '操作人（审核人/系统）',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_settlement_state_log_sid (settlement_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='结算单状态变更记录（只增不改）';

-- 操作权限点（id 沿 V18 的 65 顺延；查询/审核/执行；列表页菜单留 S26）
INSERT INTO sys_permission (id, code, name, type)
VALUES (66, 'settlement:settle:list', '结算单列表', 2),
       (67, 'settlement:settle:query', '结算单详情', 2),
       (68, 'settlement:settle:approve', '结算审核', 2),
       (69, 'settlement:settle:execute', '结算执行', 2);
