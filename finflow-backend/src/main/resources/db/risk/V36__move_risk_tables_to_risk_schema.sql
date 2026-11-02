-- ============================================================
-- M10 风控数据与规则分离：规则 / 名单 / 决策日志迁入独立 schema finflow_risk（同实例）
-- 前置：V27 在主库建三表，本脚本把三表整体重命名到 finflow_risk；
-- 并为 risk_rule 增加当前版本号 + 新建 risk_rule_version 历史快照表（只增不改）。
-- 约定：db/risk 目录下的后续脚本必须显式限定 finflow_risk.，避免落到主库。
-- ============================================================
CREATE DATABASE IF NOT EXISTS finflow_risk
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

RENAME TABLE risk_rule TO finflow_risk.risk_rule,
    risk_event TO finflow_risk.risk_event,
    risk_blacklist TO finflow_risk.risk_blacklist;

-- 当前版本号：每次 create / update / toggle / delete 事务内 +1，历史快照写 risk_rule_version
ALTER TABLE finflow_risk.risk_rule
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '当前版本号（每次变更 +1）' AFTER remark;

CREATE TABLE finflow_risk.risk_rule_version
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_id     BIGINT UNSIGNED NOT NULL COMMENT '规则ID（risk_rule.id，规则删除后历史仍保留）',
    version     INT             NOT NULL COMMENT '版本号（与 risk_rule.version 对应）',
    rule_name   VARCHAR(64)     NOT NULL COMMENT '规则名称快照',
    rule_type   VARCHAR(32)     NOT NULL COMMENT '规则类型快照',
    params      JSON            NOT NULL COMMENT '规则参数快照',
    score       INT             NOT NULL COMMENT '风险分快照',
    priority    INT             NOT NULL COMMENT '优先级快照',
    enabled     TINYINT(1)      NOT NULL COMMENT '启停状态快照',
    remark      VARCHAR(255)             DEFAULT NULL COMMENT '备注快照',
    change_type VARCHAR(16)     NOT NULL COMMENT '变更类型 INIT存量回填/CREATE/UPDATE/TOGGLE/DELETE',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照生成时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_version (rule_id, version),
    KEY idx_rule_version_rule (rule_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='风控规则历史版本（每次变更留一行完整快照，只增不改）';

-- 存量规则回填 version=1 的初始快照：迁移时当前配置即第 1 版
INSERT INTO finflow_risk.risk_rule_version
(rule_id, version, rule_name, rule_type, params, score, priority, enabled, remark, change_type)
SELECT id, 1, rule_name, rule_type, params, score, priority, enabled, remark, 'INIT'
FROM finflow_risk.risk_rule;
