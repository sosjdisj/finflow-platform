-- ============================================================
-- S24 结算单生成：settlement_order + settlement_detail
-- 幂等核心：uk(merchant_id, settle_date) 同一商户同一结算日只一张
-- 结算金额 = Σ 清分单 merchant_income（商户应得），平台费/渠道成本
-- 留在清分口径，结算单只关心应付商户侧。
-- ============================================================

CREATE TABLE settlement_order
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    settle_no     VARCHAR(32)     NOT NULL COMMENT '结算单号（ST+日期+6位随机）',
    merchant_id   BIGINT UNSIGNED NOT NULL COMMENT '商户ID',
    settle_date   DATE            NOT NULL COMMENT '结算日（清分单的 settle_date，T+1 语义）',
    order_count   INT UNSIGNED    NOT NULL COMMENT '清分笔数',
    total_amount  BIGINT          NOT NULL COMMENT '应付商户总额（分）= Σ merchant_income',
    currency      CHAR(3)         NOT NULL DEFAULT 'CNY' COMMENT '币种',
    status        VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 生成中 → CALCULATED 已算妥（S25 扩审核态）',
    generated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    calculated_at DATETIME                 DEFAULT NULL COMMENT '算妥时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_order (merchant_id, settle_date),
    UNIQUE KEY uk_settlement_no (settle_no),
    KEY idx_settlement_order_date (settle_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='结算单';

-- 逐笔清分引用：审核与对账可追溯到每笔订单；uk(clearing_id) 防一笔清分重复入结算
CREATE TABLE settlement_detail
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    settlement_id   BIGINT UNSIGNED NOT NULL COMMENT '结算单ID（settlement_order.id）',
    clearing_id     BIGINT UNSIGNED NOT NULL COMMENT '清分单ID（clearing_order.id）',
    order_id        BIGINT UNSIGNED NOT NULL COMMENT '支付订单ID',
    pay_amount      BIGINT          NOT NULL COMMENT '支付金额（分）',
    merchant_income BIGINT          NOT NULL COMMENT '该笔商户应得（分）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_detail_clearing (clearing_id),
    KEY idx_settlement_detail_sid (settlement_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='结算单明细（逐笔清分引用）';
