-- ============================================================
-- M12 商户 OpenAPI 配额模型：按「商户 + 接口」维护限流阈值
-- qps_limit / daily_limit = 0 表示沿用平台默认值（finflow.openapi.quota.*）
-- enabled = 0 表示该商户该接口未开通 / 已停用（验签通过后也直接拒绝）
-- 计数不落库：QPS 与日配额都在 Redis 固定窗口计数，表只存策略
-- ============================================================

CREATE TABLE merchant_openapi_quota
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id BIGINT UNSIGNED NOT NULL COMMENT '商户ID',
    api_code    VARCHAR(32)     NOT NULL COMMENT '接口编码：PAYMENT_CREATE/PAYMENT_QUERY/PAYMENT_CLOSE/REFUND_CREATE/REFUND_QUERY',
    qps_limit   INT             NOT NULL DEFAULT 0 COMMENT '每秒请求上限（0=平台默认）',
    daily_limit BIGINT          NOT NULL DEFAULT 0 COMMENT '每日请求上限（0=平台默认）',
    enabled     TINYINT         NOT NULL DEFAULT 1 COMMENT '是否开通：0=停用',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_openapi_api (merchant_id, api_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='商户 OpenAPI 配额策略（按商户+接口）';
