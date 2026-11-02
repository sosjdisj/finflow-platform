-- M14：风控阈值迁至 Nacos Config（finflow-common.yaml 的 finflow.risk.thresholds）。
-- 删除 system_config 中的存量行，避免后台管理页与配置中心出现两个事实源；
-- 阈值由 backend / 风控服务从配置中心热读，不再落库。
DELETE FROM system_config
WHERE config_key IN ('risk.review-threshold', 'risk.reject-threshold');
