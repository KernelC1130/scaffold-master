package com.scaffold.vuln.ai.domain;

import java.util.Date;

/**
 * AI 对话会话行（ai_chat_session）。
 * session_id 会话ID；user_id 归属用户（sys_user.id）；title 会话标题（首问前 20 字）；
 * create_time 创建时间；update_time 最后活跃时间。
 */
public class AiChatSession {

    private Long sessionId;
    private Long userId;
    private String title;
    private Date createTime;
    private Date updateTime;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
