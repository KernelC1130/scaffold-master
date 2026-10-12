package com.scaffold.vuln.domain.dto;

/**
 * 工单新增/编辑请求体（仅业务字段）。
 * task_status 不在此列：状态流转只能走语义化动作端点（accept / fix / verify / close）。
 */
public class TaskWriteRequest {

    private Long vulnId;
    private Long handlerId;
    private String priority;
    private String progressDesc;
    private String planTime;  // yyyy-MM-dd
    private String remark;

    public Long getVulnId() { return vulnId; }
    public void setVulnId(Long vulnId) { this.vulnId = vulnId; }
    public Long getHandlerId() { return handlerId; }
    public void setHandlerId(Long handlerId) { this.handlerId = handlerId; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getProgressDesc() { return progressDesc; }
    public void setProgressDesc(String progressDesc) { this.progressDesc = progressDesc; }
    public String getPlanTime() { return planTime; }
    public void setPlanTime(String planTime) { this.planTime = planTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
