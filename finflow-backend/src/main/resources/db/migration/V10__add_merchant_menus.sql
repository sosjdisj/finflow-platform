-- ============================================================
-- S11 前端商户中心：菜单可见性权限点 + 商户中心目录 + 两个页面菜单
-- 沿用「引用式」约定：页面菜单经 permission_id 引用可见性权限点
-- ============================================================

-- 菜单可见性权限点（type = 1）
INSERT INTO sys_permission (id, code, name, type)
VALUES (5, 'merchant:merchant:manage', '商户管理菜单', 1),
       (6, 'merchant:review:manage', '商户审核菜单', 1);

-- 商户中心目录 + 页面菜单
INSERT INTO sys_menu (id, parent_id, title, path, icon, menu_type, sort_no, permission_id)
VALUES (2, 0, '商户中心', '/merchant', 'Shop', 1, 2, NULL),
       (20, 2, '商户管理', '/merchant/merchants', 'OfficeBuilding', 2, 1, 5),
       (21, 2, '商户审核', '/merchant/reviews', 'Stamp', 2, 2, 6);
