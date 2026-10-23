-- 工单到期提醒定时任务
-- 每天 09:00 扫描未完成工单（0待处理/1处理中）：已超期或 3 天内到期的，给负责人发站内信。
-- 依赖 V8__notice_module.sql 的 sys_notice / sys_notice_read。
INSERT INTO sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent,
                     status, create_by, create_time, remark)
SELECT '工单到期提醒', 'DEFAULT', 'jobTasks.scanRepairTasks()', '0 0 9 * * ?', '3', '1',
       '0', 'admin', NOW(), '扫描未完成工单：已超期或 3 天内到期，按天给负责人发站内信'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE invoke_target = 'jobTasks.scanRepairTasks()');
