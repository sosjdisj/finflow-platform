-- ============================================================
-- S14 支付渠道抽象与 Mock 渠道
-- payment_channel：渠道运营配置（启停、支付方式支持度）
-- payment_transaction：渠道交互留痕（请求/响应/渠道单号），只增不改
-- ============================================================
CREATE TABLE payment_channel
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    channel_code    VARCHAR(32)     NOT NULL COMMENT '渠道编码：MOCK_BANK/MOCK_WALLET/MOCK_CARD',
    channel_name    VARCHAR(64)     NOT NULL COMMENT '渠道名称',
    payment_methods VARCHAR(128)    NOT NULL COMMENT '支持的支付方式，逗号分隔：ALIPAY/WECHAT/BANK_CARD/QUICK_CARD',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '启停状态：1 启用 0 停用',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_code (channel_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='支付渠道配置';

-- 渠道交互流水：只增不改，禁逻辑删除
CREATE TABLE payment_transaction
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id         BIGINT UNSIGNED NOT NULL COMMENT '支付订单ID（payment_order.id）',
    channel_code     VARCHAR(32)     NOT NULL COMMENT '渠道编码',
    txn_type         VARCHAR(16)     NOT NULL COMMENT '交互类型：PAYMENT/REFUND/QUERY/REFUND_QUERY',
    channel_order_no VARCHAR(64)              DEFAULT NULL COMMENT '渠道单号（渠道受理后返回）',
    request_payload  VARCHAR(1024)            DEFAULT NULL COMMENT '发往渠道的请求内容（JSON）',
    response_payload VARCHAR(1024)            DEFAULT NULL COMMENT '渠道返回内容（JSON）',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_payment_transaction_order (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='渠道交互记录';

-- Mock 渠道初始配置：钱包（支付宝/微信）、网银、快捷卡
INSERT INTO payment_channel (channel_code, channel_name, payment_methods, enabled)
VALUES ('MOCK_WALLET', 'Mock钱包', 'ALIPAY,WECHAT', 1),
       ('MOCK_BANK', 'Mock网银', 'BANK_CARD', 1),
       ('MOCK_CARD', 'Mock快捷卡', 'QUICK_CARD', 1);
