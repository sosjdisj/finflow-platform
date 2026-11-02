-- M16：Webhook 最大重试次数迁至应用配置 finflow.webhook.max-retries（TunableConfig 读取，
-- 生产由 Nacos 的 finflow-common.yaml 覆盖）。通知服务不接 system_config（跨服务同步读），
-- 故从配置中心口径移除该行，避免后台管理页与配置文件出现两个事实源（同 M14 做法）。
DELETE FROM system_config
WHERE config_key = 'webhook.max-retries';
