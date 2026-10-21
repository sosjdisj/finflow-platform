-- ============================================================
-- S30 风控规则引擎：
-- 1) risk_rule 规则配置表：类型 + JSON 参数 + 分值 + 优先级 + 启停，管理员动态增删改
-- 2) risk_event 风险事件表：每次风控检查留痕（命中明细快照 + 总分 + 判决），只增不改
-- 3) risk_blacklist 黑名单表：用户/IP/设备三维度，命中直接拒绝
-- 4) 风控操作权限点 + 风控中心目录与四个页面菜单（S31 前端直接挂载）
-- 求值阈值（ALLOW/REVIEW/REJECT 三档）读应用配置 finflow.risk，不落库
-- ============================================================

CREATE TABLE risk_rule
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    rule_name  VARCHAR(64)     NOT NULL COMMENT '规则名称',
    rule_type  VARCHAR(32)     NOT NULL COMMENT '规则类型 AMOUNT_LIMIT单笔限额/FREQUENCY时间窗口频次/BLACKLIST黑名单/SAME_IP_MULTI_ACCOUNT同IP多账户',
    params     JSON            NOT NULL COMMENT '规则参数（按类型定义，见各求值器）',
    score      INT             NOT NULL DEFAULT 0 COMMENT '命中加的风险分（黑名单类型命中直接拒绝，分值仅留档）',
    priority   INT             NOT NULL DEFAULT 100 COMMENT '执行优先级，小者先执行',
    enabled    TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用 1启用/0停用',
    remark     VARCHAR(255)             DEFAULT NULL COMMENT '备注',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_name (rule_name),
    KEY idx_rule_enabled (enabled, priority)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='风控规则（动态增删改 + 启停，求值器按类型策略扩展）';

CREATE TABLE risk_event
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    event_no    VARCHAR(40)     NOT NULL COMMENT '事件号 RE+yyyyMMdd+6位随机',
    order_id    BIGINT UNSIGNED NOT NULL COMMENT '支付订单ID（payment_order.id）',
    merchant_id BIGINT UNSIGNED NOT NULL COMMENT '商户ID',
    amount      BIGINT          NOT NULL COMMENT '订单金额（分）',
    client_ip   VARCHAR(64)              DEFAULT NULL COMMENT '客户端IP快照',
    device_id   VARCHAR(128)             DEFAULT NULL COMMENT '设备号快照',
    hit_rules   JSON                     DEFAULT NULL COMMENT '命中规则明细快照（名称/类型/分值/说明）',
    total_score INT             NOT NULL DEFAULT 0 COMMENT '累加风险总分',
    verdict     VARCHAR(16)     NOT NULL COMMENT '判决 ALLOW放行/REVIEW人工审核/REJECT拒绝',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_no (event_no),
    KEY idx_event_order (order_id),
    KEY idx_event_merchant (merchant_id),
    KEY idx_event_verdict (verdict)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='风险事件（每次检查留痕，只增不改）';

CREATE TABLE risk_blacklist
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    dimension  VARCHAR(16)     NOT NULL COMMENT '维度 USER用户/IP地址/DEVICE设备',
    value      VARCHAR(128)    NOT NULL COMMENT '名单值（用户ID/IP/设备号）',
    reason     VARCHAR(255)             DEFAULT NULL COMMENT '拉黑原因',
    created_by VARCHAR(64)     NOT NULL COMMENT '操作人（登录名）',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dimension_value (dimension, value)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='风控黑名单（用户/IP/设备三维度，命中直接拒绝）';

-- 操作权限点（type = 2），id 沿 V26 的 83 顺延
INSERT INTO sys_permission (id, code, name, type)
VALUES (84, 'risk:rule:list', '风控规则列表', 2),
       (85, 'risk:rule:create', '新增风控规则', 2),
       (86, 'risk:rule:update', '修改风控规则', 2),
       (87, 'risk:rule:delete', '删除风控规则', 2),
       (88, 'risk:blacklist:list', '黑名单列表', 2),
       (89, 'risk:blacklist:create', '黑名单新增', 2),
       (90, 'risk:blacklist:delete', '黑名单删除', 2),
       (91, 'risk:event:list', '风险事件列表', 2),
       (92, 'risk:order:handle', '风险订单人工处理', 2);

-- 菜单可见性权限点（type = 1）
INSERT INTO sys_permission (id, code, name, type)
VALUES (93, 'risk:rule:manage', '规则管理菜单', 1),
       (94, 'risk:order:manage', '风险订单菜单', 1),
       (95, 'risk:blacklist:manage', '黑名单管理菜单', 1),
       (96, 'risk:event:manage', '风险事件菜单', 1);

-- 风控中心目录（id=5）与四个页面菜单（id = 目录 id × 10 + 序号）
INSERT INTO sys_menu (id, parent_id, title, path, icon, menu_type, sort_no, permission_id)
VALUES (5, 0, '风控中心', '/risk', 'Aim', 1, 5, NULL),
       (50, 5, '规则管理', '/risk/rules', 'Operation', 2, 1, 93),
       (51, 5, '风险订单', '/risk/orders', 'Document', 2, 2, 94),
       (52, 5, '黑名单管理', '/risk/blacklists', 'Ban', 2, 3, 95),
       (53, 5, '风险事件', '/risk/events', 'Bell', 2, 4, 96);
