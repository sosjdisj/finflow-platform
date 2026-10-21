-- ============================================================
-- S26 前端清分结算页面：资金中心下新增 清分记录 / 结算管理 页面菜单
-- 沿用「引用式」约定：页面菜单经 permission_id 引用可见性权限点
-- 清分查询操作权限点（S23 留待清分页落的口子）在此一并落库
-- ============================================================

-- 清分查询操作权限点（type = 2），id 沿 V22 的 69 顺延
INSERT INTO sys_permission (id, code, name, type)
VALUES (70, 'clearing:clearing:query', '清分单详情', 2),
       (71, 'clearing:clearing:list', '清分单列表', 2);

-- 菜单可见性权限点（type = 1）
INSERT INTO sys_permission (id, code, name, type)
VALUES (72, 'clearing:clearing:manage', '清分记录菜单', 1),
       (73, 'settlement:settle:manage', '结算管理菜单', 1);

-- 资金中心（目录 id=4）下新增页面菜单（id = 目录 id × 10 + 序号，接 40/41 之后）
INSERT INTO sys_menu (id, parent_id, title, path, icon, menu_type, sort_no, permission_id)
VALUES (42, 4, '清分记录', '/finance/clearings', 'Tickets', 2, 3, 72),
       (43, 4, '结算管理', '/finance/settlements', 'Money', 2, 4, 73);
