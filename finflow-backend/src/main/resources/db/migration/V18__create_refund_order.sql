-- ============================================================
-- S21 退款：退款单表 + 退款状态留痕表 + 支付订单可退额度列
-- 退款状态机：APPLYING → PROCESSING → SUCCESS/FAILED，
--           APPLYING → FAILED（渠道拒绝受理），APPLYING → CLOSED（预留终态）
-- 防超退：发起退款对 payment_order 行锁，refunded_amount 申请即占用、失败释放
-- 金额一律用分（bigint）；留痕表只增不改，禁逻辑删除
-- ============================================================
CREATE TABLE refund_order
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    refund_no         VARCHAR(32)     NOT NULL COMMENT '平台退款单号（R+日期+随机6位）',
    order_id          BIGINT UNSIGNED NOT NULL COMMENT '支付订单ID（payment_order.id）',
    merchant_id       BIGINT UNSIGNED NOT NULL COMMENT '商户ID（冗余，便于列表筛选）',
    platform_order_no VARCHAR(32)     NOT NULL COMMENT '平台订单号（冗余，展示免联表）',
    refund_amount     BIGINT          NOT NULL COMMENT '本次退款金额（分）',
    currency          CHAR(3)         NOT NULL DEFAULT 'CNY' COMMENT '币种，ISO 4217，随支付订单',
    reason            VARCHAR(255)             DEFAULT NULL COMMENT '退款原因',
    channel_code      VARCHAR(32)              DEFAULT NULL COMMENT '渠道编码（受理后回填）',
    channel_refund_no VARCHAR(64)              DEFAULT NULL COMMENT '渠道退款单号（受理后回填）',
    status            VARCHAR(16)     NOT NULL DEFAULT 'APPLYING' COMMENT '状态：APPLYING/PROCESSING/SUCCESS/FAILED/CLOSED',
    fail_reason       VARCHAR(255)             DEFAULT NULL COMMENT '失败原因',
    operator          VARCHAR(64)     NOT NULL COMMENT '发起人',
    refunded_at       DATETIME                 DEFAULT NULL COMMENT '退款成功时间',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    KEY idx_refund_order (order_id),
    KEY idx_refund_merchant (merchant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='退款单';

-- 退款状态留痕：只增不改的流水表（镜像 payment_order_state_log）
CREATE TABLE refund_order_state_log
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    refund_id   BIGINT UNSIGNED NOT NULL COMMENT '退款单ID',
    from_status VARCHAR(16)     NOT NULL COMMENT '原状态',
    to_status   VARCHAR(16)     NOT NULL COMMENT '目标状态',
    reason      VARCHAR(255)             DEFAULT NULL COMMENT '变更原因',
    operator    VARCHAR(64)     NOT NULL COMMENT '操作人（渠道回调时记系统标识）',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_refund_state_log_rid (refund_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='退款单状态变更记录';

-- 支付订单补可退额度列：申请退款即占用，退款失败释放，行锁内校验防并发超退
ALTER TABLE payment_order
    ADD COLUMN refunded_amount BIGINT NOT NULL DEFAULT 0 COMMENT '已申请退款累计金额（分，申请占用、失败释放）' AFTER amount;

-- 退款操作权限点（id 沿 62 顺延；退款页面菜单留给 S22）
INSERT INTO sys_permission (id, code, name, type)
VALUES (63, 'refund:order:create', '发起退款', 2),
       (64, 'refund:order:query', '退款详情', 2),
       (65, 'refund:order:list', '退款列表', 2);

-- 流水业务类型注释补 REFUND（退款成功出账使用）
ALTER TABLE account_ledger
    MODIFY COLUMN biz_type VARCHAR(32) NOT NULL COMMENT '业务类型：PAYMENT/REFUND/SETTLEMENT/ADJUSTMENT';