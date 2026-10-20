package com.scaffold.system.job.controller;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.system.job.domain.SysJob;
import com.scaffold.system.job.domain.SysJobLog;
import com.scaffold.system.job.service.SysJobLogService;
import com.scaffold.system.job.service.SysJobService;
import com.scaffold.system.job.util.CronUtils;
import com.scaffold.system.job.util.JobConstants;
import com.scaffold.system.job.util.JobInvokeUtil;
import com.scaffold.system.job.util.SpringContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * 定时任务管理（系统监控 → 定时任务）。
 */
@RestController
@RequestMapping("/api/monitor")
public class MonitorJobController {

    private final SysJobService jobService;
    private final SysJobLogService logService;

    public MonitorJobController(SysJobService jobService, SysJobLogService logService) {
        this.jobService = jobService;
        this.logService = logService;
    }

    @GetMapping("/jobs")
    @RequiresPermission("monitor:job:list")
    public R<PageResult<SysJob>> jobs(PageQuery query,
                                      @RequestParam(required = false) String jobName) {
        int pageNum = query.normalizedPageNum();
        int pageSize = query.normalizedPageSize();
        return R.success(PageResult.of(
                jobService.selectJobPage(jobName, (pageNum - 1) * pageSize, pageSize),
                jobService.countJobPage(jobName),
                pageNum, pageSize));
    }

    @GetMapping("/jobs/{jobId}")
    @RequiresPermission("monitor:job:query")
    public R<SysJob> job(@PathVariable Long jobId) {
        return R.success(jobService.selectJobById(jobId));
    }

    @PostMapping("/jobs")
    @RequiresPermission("monitor:job:add")
    @OperLog(module = "定时任务", action = "新增任务")
    public R<Void> add(@RequestBody SysJob job) throws Exception {
        if (!CronUtils.isValid(job.getCronExpression())) {
            return R.error(400, "cron 表达式无效：" + CronUtils.getInvalidMessage(job.getCronExpression()));
        }
        checkInvokeTargetWhiteList(job.getInvokeTarget());
        job.setCreateBy(currentUser());
        jobService.insertJob(job);
        return R.success(null);
    }

    @PutMapping("/jobs")
    @RequiresPermission("monitor:job:edit")
    @OperLog(module = "定时任务", action = "编辑任务")
    public R<Void> edit(@RequestBody SysJob job) throws Exception {
        if (!CronUtils.isValid(job.getCronExpression())) {
            return R.error(400, "cron 表达式无效：" + CronUtils.getInvalidMessage(job.getCronExpression()));
        }
        checkInvokeTargetWhiteList(job.getInvokeTarget());
        job.setUpdateBy(currentUser());
        jobService.updateJob(job);
        return R.success(null);
    }

    @DeleteMapping("/jobs/{jobIds}")
    @RequiresPermission("monitor:job:remove")
    @OperLog(module = "定时任务", action = "删除任务")
    public R<Void> remove(@PathVariable Long[] jobIds) throws Exception {
        jobService.deleteJobByIds(jobIds);
        return R.success(null);
    }

    @PutMapping("/jobs/{jobId}/change-status")
    @RequiresPermission("monitor:job:changeStatus")
    @OperLog(module = "定时任务", action = "切换任务状态")
    public R<Void> changeStatus(@PathVariable Long jobId, @RequestParam String status) throws Exception {
        SysJob job = new SysJob();
        job.setJobId(jobId);
        job.setStatus(status);
        job.setUpdateBy(currentUser());
        jobService.changeStatus(job);
        return R.success(null);
    }

    @PutMapping("/jobs/{jobId}/run")
    @RequiresPermission("monitor:job:run")
    @OperLog(module = "定时任务", action = "立即执行任务")
    public R<Void> run(@PathVariable Long jobId) throws Exception {
        SysJob job = new SysJob();
        job.setJobId(jobId);
        jobService.run(job);
        return R.success(null);
    }

    @GetMapping("/cron-check")
    @RequiresPermission("monitor:job:add")
    public R<Boolean> cronCheck(@RequestParam String expression) {
        return R.success(CronUtils.isValid(expression));
    }

    @GetMapping("/job-logs")
    @RequiresPermission("monitor:job:query")
    public R<PageResult<SysJobLog>> jobLogs(PageQuery query,
                                            @RequestParam(required = false) String jobName,
                                            @RequestParam(required = false) String status) {
        int pageNum = query.normalizedPageNum();
        int pageSize = query.normalizedPageSize();
        return R.success(logService.page(jobName, status, pageNum, pageSize));
    }

    @DeleteMapping("/job-logs")
    @RequiresPermission("monitor:job:remove")
    @OperLog(module = "定时任务", action = "清空执行日志")
    public R<Void> cleanJobLogs() {
        logService.cleanAll();
        return R.success(null);
    }

    /**
     * invoke_target 白名单校验：只允许任务包下的 bean/类，防任意方法调用。
     */
    private void checkInvokeTargetWhiteList(String invokeTarget) {
        boolean allowed;
        try {
            String packageName = JobInvokeUtil.substringBefore(invokeTarget, "(");
            if (JobInvokeUtil.countMatches(packageName, ".") > 1) {
                allowed = startsWithAny(invokeTarget, JobConstants.JOB_WHITELIST_STR);
            } else {
                String beanName = invokeTarget.split("\\.")[0];
                Object bean = SpringContextHolder.getBean(beanName);
                String pkg = bean.getClass().getPackage().getName();
                allowed = startsWithAny(pkg, JobConstants.JOB_WHITELIST_STR)
                        && !startsWithAny(pkg, JobConstants.JOB_ERROR_STR);
            }
        } catch (Exception ex) {
            allowed = false;
        }
        if (!allowed) {
            throw new IllegalArgumentException("invoke_target 不在允许调用的任务包内");
        }
    }

    private boolean startsWithAny(String value, String[] prefixes) {
        return Arrays.stream(prefixes).anyMatch(value::startsWith);
    }

    private String currentUser() {
        return "admin";
    }
}
