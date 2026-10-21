-- ============================================================
-- S34 前端：Webhook 与通知页面
-- 1) notification 加 read_at（站内信已读时间，NULL=未读）：报告 S34 要求「铃铛 + 已读」，
--    S33 只做了发送记录，已读支撑按 S34 范围最小补齐——管理端统一已读（运营处理视角），
--    不做每用户已读表（通知收件人当前均为商户，无面向个人的收件箱语义）
-- 2) 权限点：100 标记已读（操作）、101/102 两页菜单可见性（type=1）
-- 3) 菜单：消息中心目录（id=6）+ Webhook 投递日志（60）/ 站内信（61）两个页面菜单，
--    id 沿「目录id×10+序号」约定
-- ============================================================

ALTER TABLE notification
    ADD COLUMN read_at DATETIME DEFAULT NULL COMMENT '站内信已读时间（NULL=未读，管理端标记）' AFTER content;

-- 权限点（id 沿 90 段顺延）
INSERT INTO sys_permission (id, code, name, type)
VALUES (100, 'notification:read', '站内信标记已读', 2),
       (101, 'webhook:event:manage', 'Webhook 投递日志菜单', 1),
       (102, 'notification:manage', '站内信菜单', 1);

-- 消息中心菜单
INSERT INTO sys_menu (id, parent_id, title, path, icon, menu_type, sort_no, permission_id)
VALUES (6, 0, '消息中心', '/messages', 'Message', 1, 6, NULL),
       (60, 6, 'Webhook 投递日志', '/messages/webhooks', 'Connection', 2, 1, 101),
       (61, 6, '站内信', '/messages/notifications', 'ChatDotRound', 2, 2, 102);
