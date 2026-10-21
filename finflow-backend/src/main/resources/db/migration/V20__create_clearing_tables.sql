-- ============================================================
-- S23 清分：clearing_order + clearing_detail + merchant_tier_rate
-- 渠道成本：payment_channel 补 cost_rate_bps（万分比）
-- 分账取整规则（设计确认）：平台手续费与渠道成本各自向下取整，
-- 商户得 = 支付金额 − 平台手续费 − 渠道成本（余数归商户，平台吸收分差），
-- 三方向金额之和恒等于支付金额。
-- 清分只计算记账，不动任何账户余额（结算阶段 S25 才动账）。
-- ============================================================

-- 清分单：一笔支付成功订单一张；uk(order_id) 防重复清分（与事件去重双保险）
CREATE TABLE clearing_order
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id          BIGINT UNSIGNED NOT NULL COMMENT '支付订单ID（payment_order.id）',
    merchant_id       BIGINT UNSIGNED NOT NULL COMMENT '商户ID',
    platform_order_no VARCHAR(64)     NOT NULL COMMENT '平台订单号',
    pay_amount        BIGINT          NOT NULL COMMENT '支付金额（分）',
    merchant_income   BIGINT          NOT NULL COMMENT '商户应得（分）',
    platform_fee      BIGINT          NOT NULL COMMENT '平台手续费（分）',
    channel_cost      BIGINT          NOT NULL COMMENT '渠道成本（分）',
    currency          CHAR(3)         NOT NULL DEFAULT 'CNY' COMMENT '币种',
    channel_code      VARCHAR(32)     NOT NULL COMMENT '实际收款渠道编码',
    settle_date       DATE            NOT NULL COMMENT '支付成功日（结算按商户+日期汇总的维度）',
    status            VARCHAR(16)     NOT NULL DEFAULT 'CLEARED' COMMENT '状态：清分即终态 CLEARED',
    cleared_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '清分时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_clearing_order_order (order_id),
    KEY idx_clearing_order_merchant_date (merchant_id, settle_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='清分单';

-- 分账明细：每张清分单固定 3 行（商户得/平台手续费/渠道成本），金额均为正数
CREATE TABLE clearing_detail
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    clearing_id  BIGINT UNSIGNED NOT NULL COMMENT '清分单ID（clearing_order.id）',
    party_type   VARCHAR(16)     NOT NULL COMMENT '分账方向：MERCHANT/PLATFORM/CHANNEL',
    party_id     BIGINT UNSIGNED          DEFAULT NULL COMMENT '主体ID：MERCHANT=商户ID / PLATFORM=0 / CHANNEL=NULL',
    channel_code VARCHAR(32)              DEFAULT NULL COMMENT '渠道编码（CHANNEL 行填）',
    amount       BIGINT          NOT NULL COMMENT '分账金额（分，正数）',
    remark       VARCHAR(128)             DEFAULT NULL COMMENT '说明（费率口径等）',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_clearing_detail (clearing_id, party_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='清分分账明细';

-- 阶梯费率（S10 扩展，1 商 N 档）：命中「amount_from ≤ 支付金额」的最大档；
-- 商户存在阶梯配置时覆盖 merchant_rate 的比例费率（固定费仍叠加）
CREATE TABLE merchant_tier_rate
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id BIGINT UNSIGNED NOT NULL COMMENT '商户ID',
    amount_from BIGINT          NOT NULL COMMENT '阶梯下限（分，含；最低档为 0 起步）',
    rate_bps    BIGINT          NOT NULL COMMENT '该档比例费率（万分比）',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_tier_rate (merchant_id, amount_from)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='商户阶梯费率';

-- 渠道成本费率（万分比，NULL 视为 0）；Mock 渠道种子统一 1%（100bps）
ALTER TABLE payment_channel
    ADD COLUMN cost_rate_bps BIGINT DEFAULT NULL COMMENT '渠道成本费率（万分比，NULL=0）' AFTER payment_methods;

UPDATE payment_channel
SET cost_rate_bps = 100;
