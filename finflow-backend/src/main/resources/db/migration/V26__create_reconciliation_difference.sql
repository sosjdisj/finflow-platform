-- ============================================================
-- S29 差异处理与对账前端：
-- 1) reconciliation_difference 差异单表：人工在对账结果页对非 MATCHED 记录发起，
--    处理流 发现异常 → 人工审核确认 → 补单/调账执行 → 关闭（处理成功即关闭，同事务）
-- 2) 差异处理操作权限点 + 对账/差异两个页面菜单（引用式：菜单经 permission_id 引用可见性权限点）
-- 快照字段冗余自匹配记录：匹配重跑清重建记录后，差异单信息仍完整可读
-- ============================================================

CREATE TABLE reconciliation_difference
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    diff_no           VARCHAR(32)     NOT NULL COMMENT '差异单号 DF+yyyyMMdd+6位随机',
    task_id           BIGINT UNSIGNED NOT NULL COMMENT '来源对账任务ID（reconciliation_task.id）',
    record_id         BIGINT UNSIGNED          DEFAULT NULL COMMENT '来源匹配记录ID（匹配重跑后可能已重建，仅留痕）',
    diff_type         VARCHAR(24)     NOT NULL COMMENT '差异类型 AMOUNT_MISMATCH/STATUS_MISMATCH/MISSING_IN_PLATFORM/MISSING_IN_CHANNEL',
    status            VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/CONFIRMED/CLOSED/IGNORED',
    channel_order_no  VARCHAR(64)              DEFAULT NULL COMMENT '渠道单号（快照）',
    merchant_order_no VARCHAR(64)     NOT NULL COMMENT '商户单号（快照，匹配关联键）',
    bill_amount       BIGINT                   DEFAULT NULL COMMENT '渠道侧金额（快照，分）',
    platform_amount   BIGINT                   DEFAULT NULL COMMENT '平台侧金额（快照，分）',
    bill_status       VARCHAR(16)              DEFAULT NULL COMMENT '渠道侧状态（快照）',
    platform_status   VARCHAR(16)              DEFAULT NULL COMMENT '平台侧状态（快照）',
    platform_order_no VARCHAR(32)              DEFAULT NULL COMMENT '平台订单号（快照）',
    remark            VARCHAR(255)             DEFAULT NULL COMMENT '差异说明（来自匹配记录）',
    handle_type       VARCHAR(16)              DEFAULT NULL COMMENT '处理方式 ADJUST调账/SUPPLEMENT补单（确认时选定）',
    handle_remark     VARCHAR(255)             DEFAULT NULL COMMENT '审核确认说明',
    handle_result     VARCHAR(255)             DEFAULT NULL COMMENT '处理结果（补单凭证/调账说明）',
    ledger_no         VARCHAR(40)              DEFAULT NULL COMMENT '调账流水号（ADJUST 执行后回写，指向 account_ledger.biz_no）',
    confirmed_by      VARCHAR(64)              DEFAULT NULL COMMENT '确认人（登录名）',
    confirmed_at      DATETIME                 DEFAULT NULL COMMENT '确认时间',
    handled_by        VARCHAR(64)              DEFAULT NULL COMMENT '处理人（登录名）',
    handled_at        DATETIME                 DEFAULT NULL COMMENT '处理时间',
    created_by        VARCHAR(64)     NOT NULL COMMENT '发起人（登录名）',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_diff_no (diff_no),
    KEY idx_diff_task (task_id),
    KEY idx_diff_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='对账差异单（人工处理流，调账走 ledger 调账类型流水）';

-- 操作权限点（type = 2），id 沿 V25 的 77 顺延
INSERT INTO sys_permission (id, code, name, type)
VALUES (78, 'reconciliation:diff:create', '发起差异处理', 2),
       (79, 'reconciliation:diff:list', '差异单列表', 2),
       (80, 'reconciliation:diff:query', '差异单详情', 2),
       (81, 'reconciliation:diff:handle', '差异处理执行', 2);

-- 菜单可见性权限点（type = 1）
INSERT INTO sys_permission (id, code, name, type)
VALUES (82, 'reconciliation:task:manage', '对账管理菜单', 1),
       (83, 'reconciliation:diff:manage', '差异处理菜单', 1);

-- 资金中心（目录 id=4）下新增页面菜单（id = 目录 id × 10 + 序号，接 42/43 之后）
INSERT INTO sys_menu (id, parent_id, title, path, icon, menu_type, sort_no, permission_id)
VALUES (44, 4, '对账管理', '/finance/reconciliations', 'DocumentChecked', 2, 5, 82),
       (45, 4, '差异处理', '/finance/recon-differences', 'Warning', 2, 6, 83);
