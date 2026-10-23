package com.scaffold.system.notice.domain.dto;

/**
 * 发送站内通知的请求体。
 * recipientId 为空表示全员公告；填了则只有该用户可见。
 */
public class NoticeSendRequest {

    private String title;
    private String content;
    /** 分类：notice 公告、alert 告警、task 待办提醒 */
    private String category;
    /** 级别：info、warn、danger */
    private String level;
    /** 单个接收人（兼容字段） */
    private Long recipientId;
    /** 多个接收人；与 recipientId 同时存在时以本字段为准，空表示全员 */
    private java.util.List<Long> recipientIds;
    private String bizType;
    private String bizId;
    private String dedupeKey;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public Long getRecipientId() { return recipientId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
    public java.util.List<Long> getRecipientIds() { return recipientIds; }
    public void setRecipientIds(java.util.List<Long> recipientIds) { this.recipientIds = recipientIds; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }
    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }
}
