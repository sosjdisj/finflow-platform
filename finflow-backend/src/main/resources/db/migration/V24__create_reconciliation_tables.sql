-- ============================================================
-- S27 对账任务与账单解析（输入侧，不做匹配）：
-- 1) reconciliation_task 对账任务 + uk(channel_code, bill_date) 拒绝同渠道同日重复上传
-- 2) reconciliation_bill_item 渠道账单暂存表（解析入库，只增不改；匹配在 S28）
-- 3) 操作权限点（页面菜单归后续前端步骤）
-- ============================================================

CREATE TABLE reconciliation_task
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_no       VARCHAR(32)     NOT NULL COMMENT '任务号 R+yyyyMMdd+6位随机',
    channel_code  VARCHAR(32)     NOT NULL COMMENT '渠道编码（payment_channel.channel_code）',
    bill_date     DATE            NOT NULL COMMENT '账单日期',
    file_name     VARCHAR(255)    NOT NULL COMMENT '上传文件原始名',
    file_path     VARCHAR(512)    NOT NULL COMMENT '本地存储路径',
    status        VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/PARSING/COMPLETED/FAILED',
    total_rows    INT             NOT NULL DEFAULT 0 COMMENT 'CSV 数据行数（不含表头）',
    valid_rows    INT             NOT NULL DEFAULT 0 COMMENT '成功入库行数',
    error_message VARCHAR(500)             DEFAULT NULL COMMENT '解析失败原因',
    uploaded_by   VARCHAR(64)     NOT NULL COMMENT '上传人（登录名）',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no),
    UNIQUE KEY uk_channel_bill_date (channel_code, bill_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='对账任务（渠道账单上传与解析）';

-- 模拟账单 CSV 格式（S27 定义）：表头 channel_order_no,merchant_order_no,amount,status,bill_date
-- amount 单位为分（整数）；status 为渠道侧状态（SUCCESS/REFUNDED/FAILED）
CREATE TABLE reconciliation_bill_item
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id          BIGINT UNSIGNED NOT NULL COMMENT '对账任务ID（reconciliation_task.id）',
    row_no           INT             NOT NULL COMMENT 'CSV 行号（数据行从 1 起）',
    channel_order_no VARCHAR(64)     NOT NULL COMMENT '渠道单号',
    merchant_order_no VARCHAR(64)    NOT NULL COMMENT '商户单号',
    amount           BIGINT          NOT NULL COMMENT '金额（分）',
    bill_status      VARCHAR(16)     NOT NULL COMMENT '渠道侧状态 SUCCESS/REFUNDED/FAILED',
    bill_date        DATE            NOT NULL COMMENT '账单日期',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_row (task_id, row_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='渠道账单暂存（只增不改，S28 匹配用）';

-- 操作权限点（type = 2）
INSERT INTO sys_permission (id, code, name, type)
VALUES (74, 'reconciliation:task:list', '对账任务列表', 2),
       (75, 'reconciliation:task:query', '对账任务详情', 2),
       (76, 'reconciliation:task:upload', '上传渠道账单', 2);
