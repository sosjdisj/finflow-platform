-- ============================================================
-- S37 系统配置中心：
-- 1) system_config 表：key / value / 类型 / 描述，key 唯一
-- 2) seed 收拢散落配置（默认值与后端 ConfigKeys 定义保持一致，改动需两边同步）：
--    全局单笔限额 / 默认费率 / 订单过期时间 / 风控阈值 / Webhook 重试 / 结算时间 / 支付开关
-- 3) 权限点（id 沿 V32 的 104 顺延）+ 系统管理下「系统配置」菜单（id=14，接 13 之后）
-- ============================================================

CREATE TABLE system_config
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key   VARCHAR(100)    NOT NULL COMMENT '配置键（与 ConfigKeys 定义一一对应，不可增删）',
    config_value VARCHAR(500)    NOT NULL COMMENT '配置值（字符串存储，按 value_type 解析）',
    value_type   VARCHAR(20)     NOT NULL COMMENT '值类型：NUMBER/STRING/BOOLEAN/JSON',
    description  VARCHAR(200)    NOT NULL COMMENT '配置说明（管理页展示）',
    updated_by   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最后修改人用户ID（0=系统初始化）',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_config_key (config_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='系统配置（运行时可调参数，修改后删缓存即刷新，不重启生效）';

-- 初始配置（updated_by=0 表示系统初始化）
INSERT INTO system_config (config_key, config_value, value_type, description, updated_by)
VALUES ('payment.amount.single-limit', '10000000', 'NUMBER', '全局单笔支付限额（分），下单金额超过直接拒绝', 0),
       ('merchant.rate.default-bps', '60', 'NUMBER', '商户未配置费率时的平台默认费率（万分比，60=0.6%）', 0),
       ('payment.order.expire-hours', '2', 'NUMBER', '支付订单默认过期时间（小时），下单未传过期时间时使用', 0),
       ('risk.review-threshold', '30', 'NUMBER', '风控人工审核阈值：风险总分达到即转人工审核', 0),
       ('risk.reject-threshold', '60', 'NUMBER', '风控直接拒绝阈值：风险总分达到即拒绝交易', 0),
       ('webhook.max-retries', '5', 'NUMBER', 'Webhook 投递最大重试次数，耗尽后等待手动重试', 0),
       ('settlement.generate-time', '01:00', 'STRING', '结算单生成时间（每日 HH:mm），修改后次日生效', 0),
       ('switch.payment.create', 'true', 'BOOLEAN', '支付下单总开关，关闭后拒绝新支付订单', 0);

-- 权限点（type = 2 操作 / type = 1 菜单可见性）
INSERT INTO sys_permission (id, code, name, type)
VALUES (105, 'system:config:list', '系统配置查询', 2),
       (106, 'system:config:update', '系统配置修改', 2),
       (107, 'system:config:manage', '系统配置菜单', 1);

-- 系统管理目录（id=1）下新增页面菜单
INSERT INTO sys_menu (id, parent_id, title, path, icon, menu_type, sort_no, permission_id)
VALUES (14, 1, '系统配置', '/system/configs', 'Tools', 2, 5, 107);
