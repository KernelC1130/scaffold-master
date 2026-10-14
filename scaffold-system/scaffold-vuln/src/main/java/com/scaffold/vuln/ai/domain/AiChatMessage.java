package com.scaffold.vuln.ai.domain;

import java.util.Date;

/**
 * AI 对话消息行（ai_chat_message）。
 * id 消息ID；session_id 会话ID；role 角色（user/assistant）；content 消息内容；create_time 创建时间。
 */
public class AiChatMessage {

    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
