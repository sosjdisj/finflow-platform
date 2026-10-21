-- ============================================================
-- S15 支付主流程串通
-- payment_callback：渠道回调记录，只增不改
-- ============================================================
CREATE TABLE payment_callback
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id         BIGINT UNSIGNED NOT NULL COMMENT '支付订单ID（payment_order.id）',
    channel_code     VARCHAR(32)     NOT NULL COMMENT '渠道编码',
    channel_order_no VARCHAR(64)              DEFAULT NULL COMMENT '渠道单号',
    result           VARCHAR(16)     NOT NULL COMMENT '回调结果：SUCCESS/FAILED',
    payload          VARCHAR(1024)            DEFAULT NULL COMMENT '回调内容（JSON）',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_payment_callback_order (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='渠道回调记录';
