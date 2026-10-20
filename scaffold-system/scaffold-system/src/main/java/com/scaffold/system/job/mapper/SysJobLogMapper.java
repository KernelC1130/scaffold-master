package com.scaffold.system.job.mapper;

import com.scaffold.system.job.domain.SysJobLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 定时任务执行日志 Mapper。
 */
@Mapper
public interface SysJobLogMapper {

    @Insert("INSERT INTO sys_job_log (job_name, job_group, invoke_target, job_message, status, exception_info, " +
            "start_time, end_time, create_time) VALUES (#{jobName}, #{jobGroup}, #{invokeTarget}, #{jobMessage}, " +
            "#{status}, #{exceptionInfo}, #{startTime}, #{endTime}, NOW())")
    int insertLog(SysJobLog log);

    @Select("""
            <script>
            SELECT job_log_id AS jobLogId, job_name AS jobName, job_group AS jobGroup, invoke_target AS invokeTarget,
                   job_message AS jobMessage, status, exception_info AS exceptionInfo,
                   start_time AS startTime, end_time AS endTime
            FROM sys_job_log
            <where>
                <if test="jobName != null and jobName != ''">AND job_name LIKE CONCAT('%', #{jobName}, '%')</if>
                <if test="status != null and status != ''">AND status = #{status}</if>
            </where>
            ORDER BY job_log_id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<SysJobLog> selectLogPage(@Param("jobName") String jobName, @Param("status") String status,
                                  @Param("offset") int offset, @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_job_log
            <where>
                <if test="jobName != null and jobName != ''">AND job_name LIKE CONCAT('%', #{jobName}, '%')</if>
                <if test="status != null and status != ''">AND status = #{status}</if>
            </where>
            </script>
            """)
    long countLogPage(@Param("jobName") String jobName, @Param("status") String status);

    @Delete("DELETE FROM sys_job_log")
    int deleteLogAll();
}
