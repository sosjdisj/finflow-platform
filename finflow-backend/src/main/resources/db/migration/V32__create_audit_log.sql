-- ============================================================
-- S35 审计日志：
-- 1) audit_log 审计日志表：谁/何时/从哪个IP/做了什么 + before/after 快照 + 结果，只增不改
-- 2) 权限点：103 查询（操作）、104 导出（操作）；S35 明确不做前端页面与菜单
-- 3) 写入由 @Audited AOP 切面 + AuditRecorder 快照提交统一完成，不提供删除/修改入口
-- ============================================================

CREATE TABLE audit_log
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    actor_id        BIGINT UNSIGNED NOT NULL COMMENT '操作人用户ID',
    actor_username  VARCHAR(50)     NOT NULL COMMENT '操作人登录名',
    action          VARCHAR(64)     NOT NULL COMMENT '操作动作（如 MERCHANT_RATE_SET）',
    resource_type   VARCHAR(32)     NOT NULL COMMENT '资源类型 MERCHANT/SETTLEMENT/ACCOUNT/RISK_RULE/ROLE',
    resource_id     VARCHAR(64)     NOT NULL COMMENT '资源标识（商户ID/结算单ID/账户ID等）',
    before_snapshot TEXT                     DEFAULT NULL COMMENT '变更前快照（JSON，首次设置为 NULL）',
    after_snapshot  TEXT                     DEFAULT NULL COMMENT '变更后快照（JSON）',
    result          VARCHAR(16)     NOT NULL COMMENT '结果 SUCCESS成功/FAILED失败',
    error_message   VARCHAR(255)             DEFAULT NULL COMMENT '失败原因（result=FAILED 时）',
    client_ip       VARCHAR(45)              DEFAULT NULL COMMENT '客户端IP（X-Forwarded-For 优先）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_action_created (action, created_at),
    KEY idx_actor (actor_id),
    KEY idx_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='审计日志（敏感操作留痕，只增不改，无删除/修改入口）';

-- 操作权限点（type = 2），id 沿 V31 的 102 顺延
INSERT INTO sys_permission (id, code, name, type)
VALUES (103, 'audit:log:list', '审计日志查询', 2),
       (104, 'audit:log:export', '审计日志导出', 2);
