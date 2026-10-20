package com.scaffold.system.job.quartz;

import com.scaffold.system.job.domain.SysJob;
import com.scaffold.system.job.domain.SysJobLog;
import com.scaffold.system.job.service.SysJobLogService;
import com.scaffold.system.job.util.JobInvokeUtil;
import com.scaffold.system.job.util.ScheduleConstants;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;

/**
 * quartz 调用抽象：统一包一层执行日志（sys_job_log）。
 */
public abstract class AbstractQuartzJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(AbstractQuartzJob.class);

    @Autowired
    private SysJobLogService jobLogService;

    @Override
    public void execute(JobExecutionContext context) {
        SysJob sysJob = new SysJob();
        Object prop = context.getMergedJobDataMap().get(ScheduleConstants.TASK_PROPERTIES);
        if (prop instanceof SysJob source) {
            BeanUtils.copyProperties(source, sysJob);
        }
        Date start = new Date();
        try {
            JobInvokeUtil.invokeMethod(sysJob);
            after(sysJob, null, start);
        } catch (Exception e) {
            log.error("定时任务执行异常 job={}", sysJob.getJobName(), e);
            after(sysJob, e, start);
        }
    }

    /** 子类可覆盖的执行钩子（当前两个执行器行为一致，仅并发策略不同） */
    protected void doExecuteInternal(JobExecutionContext context) {
    }

    private void after(SysJob sysJob, Exception ex, Date start) {
        try {
            SysJobLog jobLog = new SysJobLog();
            jobLog.setJobName(sysJob.getJobName());
            jobLog.setJobGroup(sysJob.getJobGroup());
            jobLog.setInvokeTarget(sysJob.getInvokeTarget());
            jobLog.setStatus(ex == null ? "0" : "1");
            jobLog.setJobMessage(ex == null ? sysJob.getJobName() + " 执行成功" : "执行失败");
            jobLog.setExceptionInfo(ex == null ? "" : String.valueOf(ex.getMessage()));
            jobLog.setStartTime(start);
            jobLog.setEndTime(new Date());
            jobLogService.addJobLog(jobLog);
        } catch (Exception logError) {
            log.error("定时任务日志写入失败", logError);
        }
    }
}
