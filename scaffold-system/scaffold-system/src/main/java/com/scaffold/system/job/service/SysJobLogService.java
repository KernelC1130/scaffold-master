package com.scaffold.system.job.service;

import com.scaffold.common.domain.PageResult;
import com.scaffold.system.job.domain.SysJobLog;
import com.scaffold.system.job.mapper.SysJobLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 定时任务执行日志服务。
 */
@Service
public class SysJobLogService {

    @Autowired
    private SysJobLogMapper logMapper;

    public void addJobLog(SysJobLog jobLog) {
        logMapper.insertLog(jobLog);
    }

    public PageResult<SysJobLog> page(String jobName, String status, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<SysJobLog> rows = logMapper.selectLogPage(jobName, status, offset, pageSize);
        long total = logMapper.countLogPage(jobName, status);
        return PageResult.of(rows, total, pageNum, pageSize);
    }

    public int cleanAll() {
        return logMapper.deleteLogAll();
    }
}
