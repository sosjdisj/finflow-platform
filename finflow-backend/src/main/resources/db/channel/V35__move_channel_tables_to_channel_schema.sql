-- ============================================================
-- M07 渠道数据分离：渠道配置与渠道交互记录迁入独立 schema finflow_channel（同实例）
-- 前置：V12 在主库建表、V20 在主库补 cost_rate_bps，本脚本把两张表整体重命名到 finflow_channel
-- 约定：db/channel 目录下的后续脚本必须显式限定 finflow_channel.，避免落到主库
-- ============================================================
CREATE DATABASE IF NOT EXISTS finflow_channel
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

RENAME TABLE payment_channel TO finflow_channel.payment_channel,
    payment_transaction TO finflow_channel.payment_transaction;
