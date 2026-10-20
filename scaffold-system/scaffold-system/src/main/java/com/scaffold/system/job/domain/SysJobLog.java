package com.scaffold.system.job.domain;

/**
 * 定时任务执行日志（对应 sys_job_log 表）。
 */
public class SysJobLog {

    private Long jobLogId;
    private String jobName;
    private String jobGroup;
    private String invokeTarget;
    private String jobMessage;
    /** 执行状态（0正常 1失败） */
    private String status;
    private String exceptionInfo;
    private java.util.Date startTime;
    private java.util.Date endTime;

    public Long getJobLogId() { return jobLogId; }
    public void setJobLogId(Long jobLogId) { this.jobLogId = jobLogId; }
    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public String getJobGroup() { return jobGroup; }
    public void setJobGroup(String jobGroup) { this.jobGroup = jobGroup; }
    public String getInvokeTarget() { return invokeTarget; }
    public void setInvokeTarget(String invokeTarget) { this.invokeTarget = invokeTarget; }
    public String getJobMessage() { return jobMessage; }
    public void setJobMessage(String jobMessage) { this.jobMessage = jobMessage; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExceptionInfo() { return exceptionInfo; }
    public void setExceptionInfo(String exceptionInfo) { this.exceptionInfo = exceptionInfo; }
    public java.util.Date getStartTime() { return startTime; }
    public void setStartTime(java.util.Date startTime) { this.startTime = startTime; }
    public java.util.Date getEndTime() { return endTime; }
    public void setEndTime(java.util.Date endTime) { this.endTime = endTime; }
}
