-- V3__vuln_module.sql：漏洞管理业务模块（迁移自 RuoYi 版漏洞平台）
-- 内容：三张业务表 DDL、菜单（漏洞管理组 + 资产管理）、业务字典。
-- 可重复执行（IF NOT EXISTS / INSERT IGNORE）。

-- ============ 业务表 ============

CREATE TABLE IF NOT EXISTS vuln_asset (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '资产ID',
    asset_ip    VARCHAR(128) NOT NULL                COMMENT 'IP地址（IPv4）',
    asset_name  VARCHAR(128) NOT NULL                COMMENT '服务/应用名称',
    owner       VARCHAR(64)  DEFAULT ''              COMMENT '负责人',
    risk_level  CHAR(1)      NOT NULL DEFAULT '0'    COMMENT '风险等级：0低/1中/2高（由关联漏洞自动计算，不可手填）',
    status      CHAR(1)      NOT NULL DEFAULT '0'    COMMENT '状态：0正常/1停用',
    remark      VARCHAR(500) DEFAULT NULL            COMMENT '备注',
    create_by   VARCHAR(64)  DEFAULT ''              COMMENT '创建人',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   VARCHAR(64)  DEFAULT ''              COMMENT '更新人',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_vuln_asset_ip (asset_ip)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '资产表';

CREATE TABLE IF NOT EXISTS vuln_vulnerability (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '漏洞ID',
    asset_id    BIGINT       NOT NULL                COMMENT '关联资产ID（逻辑外键 vuln_asset.id）',
    vuln_title  VARCHAR(255) NOT NULL                COMMENT '漏洞标题',
    cve_no      VARCHAR(64)  DEFAULT ''              COMMENT 'CVE编号（格式 CVE-yyyy-nnnn，可空）',
    risk_level  CHAR(1)      NOT NULL DEFAULT '0'    COMMENT '风险等级：0低/1中/2高',
    vuln_status CHAR(1)      NOT NULL DEFAULT '0'    COMMENT '漏洞状态：0新发现/1修复中/2已修复/3已关闭（由修复工单自动计算，不可手填）',
    found_time  DATE         DEFAULT NULL            COMMENT '发现日期',
    remark      VARCHAR(500) DEFAULT NULL            COMMENT '备注',
    create_by   VARCHAR(64)  DEFAULT ''              COMMENT '创建人',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   VARCHAR(64)  DEFAULT ''              COMMENT '更新人',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_vuln_vuln_asset (asset_id),
    KEY idx_vuln_vuln_status (vuln_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '漏洞表';

CREATE TABLE IF NOT EXISTS vuln_repair_task (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '工单ID',
    vuln_id       BIGINT       NOT NULL                COMMENT '关联漏洞ID（逻辑外键 vuln_vulnerability.id）',
    handler_id    BIGINT       NOT NULL                COMMENT '负责人（sys_user.id）',
    task_status   CHAR(1)      NOT NULL DEFAULT '0'    COMMENT '工单状态：0待处理/1处理中/2已修复/3已验证/4已关闭（状态机 0→1→2→{3|1}→4）',
    priority      CHAR(1)      NOT NULL DEFAULT '1'    COMMENT '优先级：0低/1中/2高/3紧急',
    progress_desc VARCHAR(500) DEFAULT NULL            COMMENT '修复进度说明',
    plan_time     DATETIME     DEFAULT NULL            COMMENT '计划完成时间',
    finish_time   DATETIME     DEFAULT NULL            COMMENT '实际完成时间（状态 2/3/4 时必填）',
    verifier_id   BIGINT       DEFAULT NULL            COMMENT '验证人（sys_user.id，复测动作写入）',
    verify_remark VARCHAR(500) DEFAULT NULL            COMMENT '验证意见（复测通过/打回时必填）',
    remark        VARCHAR(500) DEFAULT NULL            COMMENT '备注',
    create_by     VARCHAR(64)  DEFAULT ''              COMMENT '创建人',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by     VARCHAR(64)  DEFAULT ''              COMMENT '更新人',
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_vuln_task_vuln (vuln_id),
    KEY idx_vuln_task_handler (handler_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '修复工单表';

-- ============ 菜单 ============
-- 漏洞管理业务组排在工作台之后（sort=2），系统管理/监控/工具顺延。

UPDATE sys_menu SET sort = 3 WHERE id = 100;
UPDATE sys_menu SET sort = 4 WHERE id = 200;
UPDATE sys_menu SET sort = 5 WHERE id = 300;

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (400, 0, '漏洞管理', 'dir', 'shield', '/vuln', '', '', 2, 1, 1, 'system', 'system'),
    (401, 400, '资产管理', 'menu', 'database', '/vuln/asset', 'vuln/asset/index', 'vuln:asset:list', 1, 1, 1, 'system', 'system'),
    (4011, 401, '新增资产', 'btn', '', '', '', 'vuln:asset:add', 1, 1, 1, 'system', 'system'),
    (4012, 401, '编辑资产', 'btn', '', '', '', 'vuln:asset:edit', 2, 1, 1, 'system', 'system'),
    (4013, 401, '删除资产', 'btn', '', '', '', 'vuln:asset:remove', 3, 1, 1, 'system', 'system'),
    (402, 400, '漏洞管理', 'menu', 'alert', '/vuln/vuln', 'vuln/vuln/index', 'vuln:vulnerability:list', 2, 1, 1, 'system', 'system'),
    (4021, 402, '新增漏洞', 'btn', '', '', '', 'vuln:vulnerability:add', 1, 1, 1, 'system', 'system'),
    (4022, 402, '编辑漏洞', 'btn', '', '', '', 'vuln:vulnerability:edit', 2, 1, 1, 'system', 'system'),
    (4023, 402, '删除漏洞', 'btn', '', '', '', 'vuln:vulnerability:remove', 3, 1, 1, 'system', 'system'),
    (403, 400, '修复工单', 'menu', 'board', '/vuln/repair', 'vuln/repair/index', 'vuln:repair:list', 3, 1, 1, 'system', 'system'),
    (4031, 403, '新增工单', 'btn', '', '', '', 'vuln:repair:add', 1, 1, 1, 'system', 'system'),
    (4032, 403, '编辑工单', 'btn', '', '', '', 'vuln:repair:edit', 2, 1, 1, 'system', 'system'),
    (4033, 403, '删除工单', 'btn', '', '', '', 'vuln:repair:remove', 3, 1, 1, 'system', 'system');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- ============ 业务字典 ============
-- tone 对应前端 Tag 色调（当前支持 ok/info/danger/neutral，中风险琥珀档阶段 1 扩展后 UPDATE）。

INSERT IGNORE INTO sys_dict_type (id, dict_name, dict_type, status, remark, create_by, update_by)
VALUES
    (10, '漏洞风险等级', 'vuln_risk_level', 1, '资产与漏洞共用的风险等级', 'system', 'system'),
    (11, '漏洞状态',     'vuln_status',     1, '漏洞状态（由修复工单自动计算）', 'system', 'system'),
    (12, '资产状态',     'vuln_asset_status', 1, '资产启用状态', 'system', 'system'),
    (13, '工单状态',     'vuln_task_status', 1, '修复工单状态机', 'system', 'system'),
    (14, '工单优先级',   'vuln_task_priority', 1, '修复工单优先级', 'system', 'system');

INSERT IGNORE INTO sys_dict_data (id, dict_type, dict_label, dict_value, sort, status, tone, is_default, create_by, update_by)
VALUES
    (200, 'vuln_risk_level', '低', '0', 1, 1, 'ok',      1, 'system', 'system'),
    (201, 'vuln_risk_level', '中', '1', 2, 1, 'info',    0, 'system', 'system'),
    (202, 'vuln_risk_level', '高', '2', 3, 1, 'danger',  0, 'system', 'system'),
    (210, 'vuln_status', '新发现', '0', 1, 1, 'danger',  1, 'system', 'system'),
    (211, 'vuln_status', '修复中', '1', 2, 1, 'info',    0, 'system', 'system'),
    (212, 'vuln_status', '已修复', '2', 3, 1, 'ok',      0, 'system', 'system'),
    (213, 'vuln_status', '已关闭', '3', 4, 1, 'neutral', 0, 'system', 'system'),
    (220, 'vuln_asset_status', '正常', '0', 1, 1, 'ok',     1, 'system', 'system'),
    (221, 'vuln_asset_status', '停用', '1', 2, 1, 'danger', 0, 'system', 'system'),
    (230, 'vuln_task_status', '待处理', '0', 1, 1, 'danger',  1, 'system', 'system'),
    (231, 'vuln_task_status', '处理中', '1', 2, 1, 'info',    0, 'system', 'system'),
    (232, 'vuln_task_status', '已修复', '2', 3, 1, 'ok',      0, 'system', 'system'),
    (233, 'vuln_task_status', '已验证', '3', 4, 1, 'ok',      0, 'system', 'system'),
    (234, 'vuln_task_status', '已关闭', '4', 5, 1, 'neutral', 0, 'system', 'system'),
    (240, 'vuln_task_priority', '低',   '0', 1, 1, 'neutral', 0, 'system', 'system'),
    (241, 'vuln_task_priority', '中',   '1', 2, 1, 'info',    1, 'system', 'system'),
    (242, 'vuln_task_priority', '高',   '2', 3, 1, 'danger',  0, 'system', 'system'),
    (243, 'vuln_task_priority', '紧急', '3', 4, 1, 'danger',  0, 'system', 'system');
