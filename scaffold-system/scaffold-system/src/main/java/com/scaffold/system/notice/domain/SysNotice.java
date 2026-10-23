package com.scaffold.system.notice.domain;

/**
 * 站内通知（对应 sys_notice 表）：公告与点对点消息共用一条模型。
 * recipientId 为空表示全员可见，否则仅指定用户可见。
 */
public class SysNotice {

    private Long id;
    private String title;
    private String content;
    /** 分类：notice 公告、alert 告警、task 待办提醒 */
    private String category;
    /** 级别：info、warn、danger */
    private String level;
    /** 接收人；空 = 全员公告 */
    private Long recipientId;
    /** 关联业务类型（如 vuln_task、vuln） */
    private String bizType;
    /** 关联业务 ID */
    private String bizId;
    /** 去重键：同一业务事项只发一次 */
    private String dedupeKey;
    /** 当前用户是否已读（查询时联表得出，不落库） */
    private Boolean readFlag;
    private String createBy;
    /** yyyy-MM-dd HH:mm，查询时由 SQL 格式化，与其余列表页口径一致 */
    private String createTime;
    private String updateBy;
    private java.util.Date updateTime;
    private Integer deleted;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }
    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }
    public Boolean getReadFlag() { return readFlag; }
    public void setReadFlag(Boolean readFlag) { this.readFlag = readFlag; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public java.util.Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(java.util.Date updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
