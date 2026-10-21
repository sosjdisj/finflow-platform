-- ============================================================
-- S07 前端用户管理页支撑：用户管理操作权限点
-- 菜单可见性权限 system:user:manage（id=1）V4 已有；
-- 操作权限 id 沿 41 段顺延（41 = user:assign-role）
-- ============================================================
INSERT INTO sys_permission (id, code, name, type)
VALUES (42, 'system:user:list', '用户查询', 2),
       (43, 'system:user:create', '用户新增', 2),
       (44, 'system:user:update', '用户修改', 2),
       (45, 'system:user:reset-password', '用户重置密码', 2);
