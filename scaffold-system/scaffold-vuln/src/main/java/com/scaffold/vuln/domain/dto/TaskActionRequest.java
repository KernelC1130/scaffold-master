package com.scaffold.vuln.domain.dto;

/**
 * 工单状态动作请求体：fix 提交修复（finishTime 必填）、verify 复测（pass + verifyRemark 必填）。
 */
public class TaskActionRequest {

    private Boolean pass;
    private String finishTime; // ISO：yyyy-MM-ddTHH:mm
    private String progressDesc;
    private String verifyRemark;

    public Boolean getPass() { return pass; }
    public void setPass(Boolean pass) { this.pass = pass; }
    public String getFinishTime() { return finishTime; }
    public void setFinishTime(String finishTime) { this.finishTime = finishTime; }
    public String getProgressDesc() { return progressDesc; }
    public void setProgressDesc(String progressDesc) { this.progressDesc = progressDesc; }
    public String getVerifyRemark() { return verifyRemark; }
    public void setVerifyRemark(String verifyRemark) { this.verifyRemark = verifyRemark; }
}
