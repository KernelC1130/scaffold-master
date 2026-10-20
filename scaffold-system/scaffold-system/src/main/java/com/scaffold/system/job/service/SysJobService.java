package com.scaffold.system.job.service;

import com.scaffold.system.job.domain.SysJob;
import com.scaffold.system.job.exception.TaskException;
import com.scaffold.system.job.mapper.SysJobMapper;
import com.scaffold.system.job.util.CronUtils;
import com.scaffold.system.job.util.ScheduleConstants;
import com.scaffold.system.job.util.ScheduleUtils;
import jakarta.annotation.PostConstruct;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 定时任务调度服务：CRUD 与 quartz 调度器联动。
 */
@Service
public class SysJobService {

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private SysJobMapper jobMapper;

    /** 启动时从 sys_job 加载全部任务到内存调度器（RAMJobStore 模式） */
    @PostConstruct
    public void init() throws SchedulerException, TaskException {
        scheduler.clear();
        List<SysJob> jobList = jobMapper.selectJobAll();
        for (SysJob job : jobList) {
            ScheduleUtils.createScheduleJob(scheduler, job);
        }
    }

    public List<SysJob> selectJobAll() {
        return jobMapper.selectJobAll();
    }

    public SysJob selectJobById(Long jobId) {
        return jobMapper.selectJobById(jobId);
    }

    public List<SysJob> selectJobPage(String jobName, int offset, int pageSize) {
        return jobMapper.selectJobPage(jobName, offset, pageSize);
    }

    public long countJobPage(String jobName) {
        return jobMapper.countJobPage(jobName);
    }

    @Transactional(rollbackFor = Exception.class)
    public int pauseJob(SysJob job) throws SchedulerException {
        int rows = jobMapper.updateJobStatus(job.getJobId(), ScheduleConstants.Status.PAUSE.getValue(), job.getUpdateBy());
        if (rows > 0) {
            scheduler.pauseJob(ScheduleUtils.getJobKey(job.getJobId(), job.getJobGroup()));
        }
        return rows;
    }

    @Transactional(rollbackFor = Exception.class)
    public int resumeJob(SysJob job) throws SchedulerException {
        int rows = jobMapper.updateJobStatus(job.getJobId(), ScheduleConstants.Status.NORMAL.getValue(), job.getUpdateBy());
        if (rows > 0) {
            scheduler.resumeJob(ScheduleUtils.getJobKey(job.getJobId(), job.getJobGroup()));
        }
        return rows;
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteJob(SysJob job) throws SchedulerException {
        int rows = jobMapper.deleteJobById(job.getJobId());
        if (rows > 0) {
            scheduler.deleteJob(ScheduleUtils.getJobKey(job.getJobId(), job.getJobGroup()));
        }
        return rows;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteJobByIds(Long[] jobIds) throws SchedulerException {
        for (Long jobId : jobIds) {
            deleteJob(selectJobById(jobId));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int changeStatus(SysJob job) throws SchedulerException {
        return ScheduleConstants.Status.NORMAL.getValue().equals(job.getStatus())
                ? resumeJob(job)
                : pauseJob(job);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean run(SysJob job) throws SchedulerException {
        SysJob properties = selectJobById(job.getJobId());
        JobDataMap dataMap = new JobDataMap();
        dataMap.put(ScheduleConstants.TASK_PROPERTIES, properties);
        JobKey jobKey = ScheduleUtils.getJobKey(job.getJobId(), job.getJobGroup());
        if (scheduler.checkExists(jobKey)) {
            scheduler.triggerJob(jobKey, dataMap);
            return true;
        }
        return false;
    }

    @Transactional(rollbackFor = Exception.class)
    public int insertJob(SysJob job) throws SchedulerException, TaskException {
        job.setStatus(ScheduleConstants.Status.PAUSE.getValue());
        int rows = jobMapper.insertJob(job);
        if (rows > 0) {
            ScheduleUtils.createScheduleJob(scheduler, job);
        }
        return rows;
    }

    @Transactional(rollbackFor = Exception.class)
    public int updateJob(SysJob job) throws SchedulerException, TaskException {
        SysJob properties = selectJobById(job.getJobId());
        int rows = jobMapper.updateJob(job);
        if (rows > 0) {
            JobKey jobKey = ScheduleUtils.getJobKey(job.getJobId(), properties.getJobGroup());
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
            ScheduleUtils.createScheduleJob(scheduler, job);
        }
        return rows;
    }

    public boolean checkCronExpressionIsValid(String cronExpression) {
        return CronUtils.isValid(cronExpression);
    }
}
