-- ============================================================
-- M17 夜间对账批处理：新增对账自动匹配时间配置项
-- 与 ConfigKeys.RECONCILIATION_MATCH_TIME 保持一致（默认 02:00，修改后次日生效）
-- ============================================================

INSERT INTO system_config (config_key, config_value, value_type, description, updated_by)
VALUES ('reconciliation.match-time', '02:00', 'STRING', '对账自动匹配时间（每日 HH:mm），修改后次日生效', 0);
