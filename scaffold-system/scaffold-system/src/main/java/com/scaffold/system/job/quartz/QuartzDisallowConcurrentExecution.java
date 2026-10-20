package com.scaffold.system.job.quartz;

import org.quartz.JobExecutionContext;
import org.quartz.DisallowConcurrentExecution;

/**
 * 禁止并发的任务执行器。
 */
@DisallowConcurrentExecution
public class QuartzDisallowConcurrentExecution extends AbstractQuartzJob {

    @Override
    protected void doExecuteInternal(JobExecutionContext context) {
        // 逻辑统一在父类 execute 内完成，这里为占位钩子
    }
}
