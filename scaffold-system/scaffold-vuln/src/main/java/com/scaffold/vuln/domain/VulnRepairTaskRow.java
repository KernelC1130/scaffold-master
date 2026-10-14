package com.scaffold.vuln.domain;

import java.time.LocalDate;

/**
 * 修复工单行（vuln_repair_task）。
 * vuln_id 关联漏洞；handler_id 负责人（sys_user.id）；task_status 状态机 0待处理/1处理中/2已修复/3已验证/4已关闭；
 * priority 优先级 0低/1中/2高/3紧急；verifier_id 验证人；verify_remark 验证意见；
 * vuln_title / vuln_risk_level / asset_name / handler_name / verifier_name 为 JOIN 展示字段。
 */
public class VulnRepairTaskRow {

    private Long id;
    private Long vulnId;
    private Long handlerId;
    private String taskStatus;
    private String priority;
    private String progressDesc;
    private LocalDate planTime;
    private String finishTime;
    private Long verifierId;
    private String verifyRemark;
    private String remark;
    private String vulnTitle;
    private String vulnRiskLevel;
    private String assetName;
    private String assetIp;
    private String handlerName;
    private String verifierName;
    private String createTime;
    private String updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVulnId() { return vulnId; }
    public void setVulnId(Long vulnId) { this.vulnId = vulnId; }
    public Long getHandlerId() { return handlerId; }
    public void setHandlerId(Long handlerId) { this.handlerId = handlerId; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getProgressDesc() { return progressDesc; }
    public void setProgressDesc(String progressDesc) { this.progressDesc = progressDesc; }
    public LocalDate getPlanTime() { return planTime; }
    public void setPlanTime(LocalDate planTime) { this.planTime = planTime; }
    public String getFinishTime() { return finishTime; }
    public void setFinishTime(String finishTime) { this.finishTime = finishTime; }
    public Long getVerifierId() { return verifierId; }
    public void setVerifierId(Long verifierId) { this.verifierId = verifierId; }
    public String getVerifyRemark() { return verifyRemark; }
    public void setVerifyRemark(String verifyRemark) { this.verifyRemark = verifyRemark; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getVulnTitle() { return vulnTitle; }
    public void setVulnTitle(String vulnTitle) { this.vulnTitle = vulnTitle; }
    public String getVulnRiskLevel() { return vulnRiskLevel; }
    public void setVulnRiskLevel(String vulnRiskLevel) { this.vulnRiskLevel = vulnRiskLevel; }
    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }
    public String getAssetIp() { return assetIp; }
    public void setAssetIp(String assetIp) { this.assetIp = assetIp; }
    public String getHandlerName() { return handlerName; }
    public void setHandlerName(String handlerName) { this.handlerName = handlerName; }
    public String getVerifierName() { return verifierName; }
    public void setVerifierName(String verifierName) { this.verifierName = verifierName; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
}
