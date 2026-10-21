-- ============================================================
-- S08 商户中心：商户主体表 + 状态变更记录表
-- 状态机：PENDING → REVIEWING → ACTIVE → SUSPENDED / CLOSED，
--        驳回与关闭统一落 CLOSED（原因记 merchant_status_log.reason）
-- ============================================================
CREATE TABLE merchant
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_no         VARCHAR(32)     NOT NULL COMMENT '商户编号（平台生成，M+日期+随机6位）',
    merchant_name       VARCHAR(128)    NOT NULL COMMENT '商户名称',
    category            VARCHAR(32)              DEFAULT NULL COMMENT '所属行业',
    contact_name        VARCHAR(64)     NOT NULL COMMENT '联系人',
    contact_phone       VARCHAR(32)     NOT NULL COMMENT '联系电话',
    contact_email       VARCHAR(128)             DEFAULT NULL COMMENT '联系邮箱',
    business_license_no VARCHAR(64)     NOT NULL COMMENT '统一社会信用代码',
    business_address    VARCHAR(255)             DEFAULT NULL COMMENT '经营地址',
    settle_cycle        VARCHAR(8)      NOT NULL DEFAULT 'T1' COMMENT '结算周期：T1 / D1',
    status              VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/REVIEWING/ACTIVE/SUSPENDED/CLOSED',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_no (merchant_no),
    UNIQUE KEY uk_merchant_name (merchant_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='商户';

-- 状态变更留痕：只增不改的流水表，禁逻辑删除
CREATE TABLE merchant_status_log
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id BIGINT UNSIGNED NOT NULL COMMENT '商户ID',
    from_status VARCHAR(16)     NOT NULL COMMENT '原状态',
    to_status   VARCHAR(16)     NOT NULL COMMENT '目标状态',
    reason      VARCHAR(255)             DEFAULT NULL COMMENT '变更原因',
    operator    VARCHAR(64)     NOT NULL COMMENT '操作人',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_merchant_status_log_mid (merchant_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='商户状态变更记录';

-- 商户管理操作权限点（id 沿 46 段顺延；页面菜单留给 S11）
INSERT INTO sys_permission (id, code, name, type)
VALUES (46, 'merchant:merchant:list', '商户查询', 2),
       (47, 'merchant:merchant:create', '商户新增', 2),
       (48, 'merchant:merchant:update', '商户修改', 2),
       (49, 'merchant:merchant:status', '商户状态变更', 2);
