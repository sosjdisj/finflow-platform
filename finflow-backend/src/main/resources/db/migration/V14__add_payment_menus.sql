-- ============================================================
-- S17 前端支付订单页面：菜单可见性权限点 + 支付中心目录 + 订单页面菜单
-- 沿用「引用式」约定：页面菜单经 permission_id 引用可见性权限点
-- ============================================================

-- 菜单可见性权限点（type = 1）
INSERT INTO sys_permission (id, code, name, type)
VALUES (7, 'payment:order:manage', '支付订单菜单', 1);

-- 支付中心目录 + 页面菜单
INSERT INTO sys_menu (id, parent_id, title, path, icon, menu_type, sort_no, permission_id)
VALUES (3, 0, '支付中心', '/payments', 'CreditCard', 1, 3, NULL),
       (30, 3, '支付订单', '/payments/orders', 'Tickets', 2, 1, 7);
