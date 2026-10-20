package com.scaffold.system.job.quartz;

import org.quartz.JobExecutionContext;

/**
 * 允许并发的任务执行器。
 */
public class QuartzJobExecution extends AbstractQuartzJob {

    @Override
    protected void doExecuteInternal(JobExecutionContext context) {
        // 逻辑统一在父类 execute 内完成，这里为占位钩子
    }
}
