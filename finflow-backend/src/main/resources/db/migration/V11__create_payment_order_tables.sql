-- ============================================================
-- S12 支付订单：订单表 + 状态变更记录表
-- 状态机：CREATED → PENDING → PROCESSING → PAID → SETTLED，
--        异常分支 PENDING/PROCESSING → FAILED，PAID → REFUNDING → REFUNDED
-- 金额一律用分（bigint）；留痕表只增不改，禁逻辑删除
-- ============================================================
CREATE TABLE payment_order
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id       BIGINT UNSIGNED NOT NULL COMMENT '商户ID',
    merchant_order_no VARCHAR(64)     NOT NULL COMMENT '商户订单号（商户侧传入）',
    platform_order_no VARCHAR(32)     NOT NULL COMMENT '平台订单号（P+日期+随机6位）',
    amount            BIGINT          NOT NULL COMMENT '订单金额（分）',
    currency          CHAR(3)         NOT NULL DEFAULT 'CNY' COMMENT '币种，ISO 4217',
    subject           VARCHAR(128)    NOT NULL COMMENT '商品标题',
    body              VARCHAR(255)             DEFAULT NULL COMMENT '商品描述',
    payment_method    VARCHAR(32)     NOT NULL COMMENT '支付方式（S14 渠道路由使用）',
    callback_url      VARCHAR(512)             DEFAULT NULL COMMENT '回调地址',
    expired_at        DATETIME        NOT NULL COMMENT '订单过期时间',
    status            VARCHAR(16)     NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/PENDING/PROCESSING/PAID/SETTLED/FAILED/REFUNDING/REFUNDED',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_order_no (platform_order_no),
    -- 商户订单号唯一：S13 业务幂等的库表前提
    UNIQUE KEY uk_merchant_order (merchant_id, merchant_order_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='支付订单';

-- 状态变更留痕：只增不改的流水表
CREATE TABLE payment_order_state_log
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id    BIGINT UNSIGNED NOT NULL COMMENT '支付订单ID',
    from_status VARCHAR(16)     NOT NULL COMMENT '原状态',
    to_status   VARCHAR(16)     NOT NULL COMMENT '目标状态',
    reason      VARCHAR(255)             DEFAULT NULL COMMENT '变更原因',
    operator    VARCHAR(64)     NOT NULL COMMENT '操作人（渠道回调/系统任务时记系统标识）',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_payment_state_log_oid (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='支付订单状态变更记录';

-- 支付订单操作权限点（id 沿 54 段顺延；前端页面不在本步范围）
INSERT INTO sys_permission (id, code, name, type)
VALUES (54, 'payment:order:create', '支付订单创建', 2),
       (55, 'payment:order:query', '支付订单查询', 2),
       (56, 'payment:order:list', '支付订单列表', 2);
