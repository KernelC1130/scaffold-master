-- V5__ai_permission.sql：AI 助手权限（悬浮窗形态无页面菜单，按钮权限挂在漏洞管理目录下）
-- 给 vuln_member（hejian 等演示账号，role 10）与 tester（test 账号，role 11）授权 AI 对话。
-- 可重复执行（INSERT IGNORE）。

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES (4001, 400, 'AI 助手对话', 'btn', '', '', '', 'vuln:ai:chat', 4, 1, 1, 'system', 'system');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (10, 4001);
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (11, 4001);
