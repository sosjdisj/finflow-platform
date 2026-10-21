-- ============================================================
-- S28 对账匹配引擎：
-- 1) reconciliation_record 匹配结果表（任务级批量匹配产出，重跑先清旧记录）
-- 2) reconciliation_task 补五类结果汇总列 + matched_at（任务完成后汇总各结果数量）
-- 3) 匹配操作权限点（结果查询页面归 S29 前端）
-- 五类结果：MATCHED / AMOUNT_MISMATCH / STATUS_MISMATCH /
--          MISSING_IN_PLATFORM（渠道有平台无）/ MISSING_IN_CHANNEL（平台有渠道无）
-- ============================================================

CREATE TABLE reconciliation_record
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id           BIGINT UNSIGNED NOT NULL COMMENT '对账任务ID（reconciliation_task.id）',
    result            VARCHAR(24)     NOT NULL COMMENT '匹配结果 MATCHED/AMOUNT_MISMATCH/STATUS_MISMATCH/MISSING_IN_PLATFORM/MISSING_IN_CHANNEL',
    bill_item_id      BIGINT UNSIGNED          DEFAULT NULL COMMENT '账单行ID（MISSING_IN_CHANNEL 时为空）',
    channel_order_no  VARCHAR(64)              DEFAULT NULL COMMENT '渠道单号（MISSING_IN_CHANNEL 时取平台交易留痕）',
    merchant_order_no VARCHAR(64)     NOT NULL COMMENT '商户单号（匹配关联键）',
    bill_amount       BIGINT                   DEFAULT NULL COMMENT '渠道侧金额（分）',
    bill_status       VARCHAR(16)              DEFAULT NULL COMMENT '渠道侧状态 SUCCESS/REFUNDED/FAILED',
    platform_amount   BIGINT                   DEFAULT NULL COMMENT '平台侧金额（分）',
    platform_status   VARCHAR(16)              DEFAULT NULL COMMENT '平台侧状态',
    platform_order_no VARCHAR(32)              DEFAULT NULL COMMENT '平台订单号',
    platform_refund_no VARCHAR(64)             DEFAULT NULL COMMENT '平台退款单号（REFUNDED 行匹配用，逗号分隔）',
    remark            VARCHAR(255)             DEFAULT NULL COMMENT '差异说明',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_record_task (task_id),
    KEY idx_record_task_result (task_id, result)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='对账匹配结果';

-- 任务补匹配汇总列：任务完成后汇总各结果数量；matched_at 非空即已执行过匹配（可重跑覆盖）
ALTER TABLE reconciliation_task
    ADD COLUMN matched_at                 DATETIME DEFAULT NULL COMMENT '最近一次匹配时间' AFTER uploaded_by,
    ADD COLUMN matched_count              INT      NOT NULL DEFAULT 0 COMMENT '匹配一致行数' AFTER matched_at,
    ADD COLUMN amount_mismatch_count      INT      NOT NULL DEFAULT 0 COMMENT '金额不一致行数' AFTER matched_count,
    ADD COLUMN status_mismatch_count      INT      NOT NULL DEFAULT 0 COMMENT '状态不一致行数' AFTER amount_mismatch_count,
    ADD COLUMN missing_in_platform_count  INT      NOT NULL DEFAULT 0 COMMENT '渠道有平台无行数' AFTER status_mismatch_count,
    ADD COLUMN missing_in_channel_count   INT      NOT NULL DEFAULT 0 COMMENT '平台有渠道无行数' AFTER missing_in_platform_count;

-- 匹配操作权限点（type = 2）
INSERT INTO sys_permission (id, code, name, type)
VALUES (77, 'reconciliation:task:match', '执行对账匹配', 2);
