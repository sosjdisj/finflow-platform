-- ============================================================
-- S20 前端资金中心页面：资金中心目录 + 账户列表/资金流水页面菜单
-- 沿用「引用式」约定：页面菜单经 permission_id 引用可见性权限点
-- account_ledger 补 remark 列：调账原因随流水留痕（加列不改历史数据，不违反只增不改）
-- ============================================================

ALTER TABLE account_ledger
    ADD COLUMN remark VARCHAR(255) NULL COMMENT '备注（调账原因等）' AFTER source;

-- 操作接口权限点（type = 2），id 沿 57 顺延
INSERT INTO sys_permission (id, code, name, type)
VALUES (58, 'account:list', '账户查询', 2),
       (59, 'ledger:adjust', '人工调账', 2),
       (60, 'ledger:record:list', '资金流水查询', 2);

-- 菜单可见性权限点（type = 1）
INSERT INTO sys_permission (id, code, name, type)
VALUES (61, 'account:account:manage', '账户列表菜单', 1),
       (62, 'ledger:record:manage', '资金流水菜单', 1);

-- 资金中心目录 + 页面菜单（目录 id 顺延为 4，页面 id = 目录 id × 10 + 序号）
INSERT INTO sys_menu (id, parent_id, title, path, icon, menu_type, sort_no, permission_id)
VALUES (4, 0, '资金中心', '/finance', 'Wallet', 1, 4, NULL),
       (40, 4, '账户列表', '/finance/accounts', 'Coin', 2, 1, 61),
       (41, 4, '资金流水', '/finance/ledgers', 'List', 2, 2, 62);
