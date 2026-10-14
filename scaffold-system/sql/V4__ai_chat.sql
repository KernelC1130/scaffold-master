-- V4__ai_chat.sql：AI 助手对话持久化（迁移自旧平台 Vulnerability-Manager 的 ai_chat_* 两表）
-- 可重复执行（IF NOT EXISTS）。
-- 注意：AI 助手菜单/权限 SQL 随前端页面定型后在 V5 补充；
--       super_admin 角色后端直接放行，当前 admin 可直接使用全部 /api/vuln/ai/* 接口。

CREATE TABLE IF NOT EXISTS ai_chat_session (
    session_id  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    user_id     BIGINT       NOT NULL                COMMENT '用户ID（sys_user.id）',
    title       VARCHAR(64)  DEFAULT ''              COMMENT '会话标题（首问前20字）',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    PRIMARY KEY (session_id),
    KEY idx_ai_chat_session_user (user_id),
    KEY idx_ai_chat_session_update (user_id, update_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI对话会话表';

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    session_id  BIGINT       NOT NULL                COMMENT '会话ID（逻辑外键 ai_chat_session.session_id）',
    role        VARCHAR(16)  NOT NULL                COMMENT '角色：user/assistant',
    content     TEXT                                 COMMENT '消息内容',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_ai_chat_message_session (session_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI对话消息表';
