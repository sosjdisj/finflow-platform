-- ============================================================
-- M12 API 凭证启用开关：停用后 OpenAPI 验签直接拒绝，凭证与历史记录保留
-- 存量行默认启用（DEFAULT 1），兼容历史数据
-- ============================================================

ALTER TABLE merchant_api_key
    ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0=停用（OpenAPI 验签拒绝）';
