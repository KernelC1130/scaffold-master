-- 站内通知模块：公告与点对点消息共用一套模型。
-- recipient_id 为空 = 全员可见；有值 = 仅该用户可见（铃铛与通知中心按同一口径查询）。

CREATE TABLE IF NOT EXISTS sys_notice (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知 ID',
    title VARCHAR(120) NOT NULL COMMENT '标题',
    content VARCHAR(2000) NOT NULL COMMENT '正文',
    category VARCHAR(16) NOT NULL DEFAULT 'notice' COMMENT '分类：notice 公告，alert 告警，task 待办提醒',
    level VARCHAR(16) NOT NULL DEFAULT 'info' COMMENT '级别：info，warn，danger',
    recipient_id BIGINT DEFAULT NULL COMMENT '接收人；为空表示全员公告',
    biz_type VARCHAR(64) DEFAULT NULL COMMENT '关联业务类型（如 vuln_task、vuln）',
    biz_id VARCHAR(64) DEFAULT NULL COMMENT '关联业务 ID',
    dedupe_key VARCHAR(160) DEFAULT NULL COMMENT '去重键：同一业务事项只发一次',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_notice_dedupe (dedupe_key),
    KEY idx_sys_notice_recipient (recipient_id, id),
    KEY idx_sys_notice_created (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知表';

CREATE TABLE IF NOT EXISTS sys_notice_read (
    notice_id BIGINT NOT NULL COMMENT '通知 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    read_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
    PRIMARY KEY (notice_id, user_id),
    KEY idx_sys_notice_read_user (user_id, notice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知已读表';

-- 菜单：系统管理 → 通知中心（读取接口只要求登录，页面所有角色可见）
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (108, 100, '通知中心', 'menu', 'bell', '/system/notice', 'system/notice/index', '', 8, 1, 1, 'system', 'system'),
    (1081, 108, '发送通知', 'btn', '', '', '', 'system:notice:add', 1, 1, 1, 'system', 'system'),
    (1082, 108, '删除通知', 'btn', '', '', '', 'system:notice:remove', 2, 1, 1, 'system', 'system');

-- 所有角色都能看到通知页（页面本身无权限位）；发送/删除按钮只授予超级管理员
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, 108 FROM sys_role r;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE parent_id = 108;

-- 字典：通知阅读状态（未读 / 已读），供通知列表的状态列使用
INSERT IGNORE INTO sys_dict_type (id, dict_name, dict_type, status, remark, create_by, update_by)
VALUES (15, '通知阅读状态', 'notice_read_status', 1, '站内通知的未读 / 已读', 'system', 'system');

INSERT IGNORE INTO sys_dict_data (id, dict_type, dict_label, dict_value, sort, status, tone, is_default, create_by, update_by)
VALUES
    (901, 'notice_read_status', '未读', '0', 1, 1, 'info', 1, 'system', 'system'),
    (902, 'notice_read_status', '已读', '1', 2, 1, 'neutral', 0, 'system', 'system');
