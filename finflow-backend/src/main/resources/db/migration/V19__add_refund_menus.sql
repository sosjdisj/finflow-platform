-- ============================================================
-- S22 前端退款管理：菜单可见性权限点 + 支付中心下的退款页面菜单
-- 沿用「引用式」约定：页面菜单经 permission_id 引用可见性权限点
-- 操作权限点 refund:order:create/query/list 已在 V18 落库（63/64/65）
-- ============================================================

-- 菜单可见性权限点（type = 1）
INSERT INTO sys_permission (id, code, name, type)
VALUES (8, 'refund:order:manage', '退款管理菜单', 1);

-- 支付中心（目录 id=3）下新增退款管理页面菜单
INSERT INTO sys_menu (id, parent_id, title, path, icon, menu_type, sort_no, permission_id)
VALUES (31, 3, '退款管理', '/payments/refunds', 'RefreshLeft', 2, 2, 8);
