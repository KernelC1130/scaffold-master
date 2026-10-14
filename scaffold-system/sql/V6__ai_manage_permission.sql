-- V6__ai_manage_permission.sql：AI 管理权限（知识库重建/状态），关联 super_admin。
-- 可重复执行（INSERT IGNORE）。

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES (4002, 400, 'AI 知识库管理', 'btn', '', '', '', 'vuln:ai:manage', 5, 1, 1, 'system', 'system');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (1, 4002);
