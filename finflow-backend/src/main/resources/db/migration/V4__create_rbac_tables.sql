-- ============================================================
-- S05 RBAC 权限模型：角色 / 权限 / 菜单 + 两张关联表
-- 约定：
--   * sys_permission.code 是权限点唯一事实源（接口鉴权 + 菜单可见性都看它）
--   * sys_menu 只管展示结构，通过 permission_id 引用权限点（可空）
--   * 内置超级管理员（SUPER_ADMIN）不插角色-权限关联，授权时统一查全量启用权限
--   * 关联表只增删不改，不做逻辑删除
-- ============================================================

CREATE TABLE sys_role
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(64)     NOT NULL COMMENT '角色编码，如 SUPER_ADMIN',
    name        VARCHAR(64)     NOT NULL COMMENT '角色名称',
    description VARCHAR(255)             DEFAULT NULL COMMENT '描述',
    built_in    TINYINT         NOT NULL DEFAULT 0 COMMENT '内置角色：1 是（禁删禁改）/ 0 否',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 禁用',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='角色';

CREATE TABLE sys_permission
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(128)    NOT NULL COMMENT '权限编码，如 system:role:list',
    name        VARCHAR(64)     NOT NULL COMMENT '权限名称',
    type        TINYINT         NOT NULL COMMENT '类型：1 菜单可见性 / 2 操作接口',
    description VARCHAR(255)             DEFAULT NULL COMMENT '描述',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 禁用',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='权限点';

CREATE TABLE sys_menu
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父菜单 ID，0 表示根节点',
    title         VARCHAR(64)     NOT NULL COMMENT '菜单标题',
    path          VARCHAR(128)             DEFAULT NULL COMMENT '前端路由路径',
    icon          VARCHAR(64)              DEFAULT NULL COMMENT '图标名',
    menu_type     TINYINT         NOT NULL DEFAULT 2 COMMENT '类型：1 目录 / 2 菜单',
    sort_no       INT             NOT NULL DEFAULT 0 COMMENT '排序号，小在前',
    permission_id BIGINT UNSIGNED          DEFAULT NULL COMMENT '关联权限点（控制菜单可见性），可空',
    visible       TINYINT         NOT NULL DEFAULT 1 COMMENT '是否显示：1 是 / 0 隐藏',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 禁用',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_sys_menu_parent (parent_id),
    KEY idx_sys_menu_permission (permission_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='菜单';

CREATE TABLE sys_user_role
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT UNSIGNED NOT NULL COMMENT '用户 ID（sys_user.id）',
    role_id    BIGINT UNSIGNED NOT NULL COMMENT '角色 ID（sys_role.id）',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role (user_id, role_id),
    KEY idx_sys_user_role_role (role_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户-角色关联';

CREATE TABLE sys_role_permission
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_id       BIGINT UNSIGNED NOT NULL COMMENT '角色 ID（sys_role.id）',
    permission_id BIGINT UNSIGNED NOT NULL COMMENT '权限 ID（sys_permission.id）',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_permission (role_id, permission_id),
    KEY idx_sys_role_permission_perm (permission_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='角色-权限关联';

-- ------------------------------------------------------------
-- 初始化数据：内置角色 / 权限点 / 菜单 / 内置超管账号
-- ------------------------------------------------------------

-- 内置超级管理员角色（授权时不看本表关联，直接查全量启用权限）
INSERT INTO sys_role (id, code, name, description, built_in, status)
VALUES (1, 'SUPER_ADMIN', '超级管理员', '内置角色：自动拥有全部权限', 1, 1);

-- 菜单可见性权限点（type = 1）
INSERT INTO sys_permission (id, code, name, type)
VALUES (1, 'system:user:manage', '用户管理菜单', 1),
       (2, 'system:role:manage', '角色管理菜单', 1),
       (3, 'system:permission:manage', '权限管理菜单', 1),
       (4, 'system:menu:manage', '菜单管理菜单', 1);

-- 操作接口权限点（type = 2）：角色
INSERT INTO sys_permission (id, code, name, type)
VALUES (11, 'system:role:list', '角色查询', 2),
       (12, 'system:role:create', '角色新增', 2),
       (13, 'system:role:update', '角色修改', 2),
       (14, 'system:role:delete', '角色删除', 2),
       (15, 'system:role:assign', '角色分配权限', 2);

-- 操作接口权限点（type = 2）：权限
INSERT INTO sys_permission (id, code, name, type)
VALUES (21, 'system:permission:list', '权限查询', 2),
       (22, 'system:permission:create', '权限新增', 2),
       (23, 'system:permission:update', '权限修改', 2),
       (24, 'system:permission:delete', '权限删除', 2);

-- 操作接口权限点（type = 2）：菜单 / 用户分配
INSERT INTO sys_permission (id, code, name, type)
VALUES (31, 'system:menu:list', '菜单查询', 2),
       (32, 'system:menu:create', '菜单新增', 2),
       (33, 'system:menu:update', '菜单修改', 2),
       (34, 'system:menu:delete', '菜单删除', 2),
       (41, 'system:user:assign-role', '用户分配角色', 2);

-- 基础菜单：系统管理目录 + 四个管理页（页面引用菜单可见性权限点）
INSERT INTO sys_menu (id, parent_id, title, path, icon, menu_type, sort_no, permission_id)
VALUES (1, 0, '系统管理', '/system', 'Setting', 1, 1, NULL),
       (10, 1, '用户管理', '/system/users', 'User', 2, 1, 1),
       (11, 1, '角色管理', '/system/roles', 'Key', 2, 2, 2),
       (12, 1, '权限管理', '/system/permissions', 'Lock', 2, 3, 3),
       (13, 1, '菜单管理', '/system/menus', 'Menu', 2, 4, 4);

-- 内置超管账号：用户名 admin / 密码 admin123（BCrypt）。
-- 若同名用户已存在（如此前手工注册过）则不重复插入，直接复用该用户作为超管主体。
INSERT INTO sys_user (username, password_hash, nickname, status)
SELECT 'admin', '$2a$10$vVA475yrDX.kqY/UjtTVT.qihTTaUUeT.s/uNk5ECW.P3dOC3KFC.', '超级管理员', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');

SET @admin_id = (SELECT id FROM sys_user WHERE username = 'admin');

-- 绑定超管角色（防重复绑定）
INSERT INTO sys_user_role (user_id, role_id)
SELECT @admin_id, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = @admin_id AND role_id = 1);
