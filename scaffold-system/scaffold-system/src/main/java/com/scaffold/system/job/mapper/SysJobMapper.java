package com.scaffold.system.job.mapper;

import com.scaffold.system.job.domain.SysJob;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 定时任务调度 Mapper。
 */
@Mapper
public interface SysJobMapper {

    @Select("SELECT job_id AS jobId, job_name AS jobName, job_group AS jobGroup, invoke_target AS invokeTarget, " +
            "cron_expression AS cronExpression, misfire_policy AS misfirePolicy, concurrent, status, " +
            "create_by AS createBy, create_time AS createTime, update_by AS updateBy, update_time AS updateTime, remark " +
            "FROM sys_job ORDER BY job_id")
    List<SysJob> selectJobAll();

    @Select("SELECT job_id AS jobId, job_name AS jobName, job_group AS jobGroup, invoke_target AS invokeTarget, " +
            "cron_expression AS cronExpression, misfire_policy AS misfirePolicy, concurrent, status, " +
            "create_by AS createBy, create_time AS createTime, update_by AS updateBy, update_time AS updateTime, remark " +
            "FROM sys_job WHERE job_id = #{jobId}")
    SysJob selectJobById(@Param("jobId") Long jobId);

    @Select("""
            <script>
            SELECT job_id AS jobId, job_name AS jobName, job_group AS jobGroup, invoke_target AS invokeTarget, status,
                   cron_expression AS cronExpression, create_time AS createTime
            FROM sys_job
            <where>
                <if test="jobName != null and jobName != ''">AND job_name LIKE CONCAT('%', #{jobName}, '%')</if>
            </where>
            ORDER BY job_id
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<SysJob> selectJobPage(@Param("jobName") String jobName, @Param("offset") int offset,
                               @Param("pageSize") int pageSize);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sys_job
            <where>
                <if test="jobName != null and jobName != ''">AND job_name LIKE CONCAT('%', #{jobName}, '%')</if>
            </where>
            </script>
            """)
    long countJobPage(@Param("jobName") String jobName);

    @Insert("INSERT INTO sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, " +
            "status, create_by, create_time, remark) VALUES (#{jobName}, #{jobGroup}, #{invokeTarget}, " +
            "#{cronExpression}, #{misfirePolicy}, #{concurrent}, #{status}, #{createBy}, NOW(), #{remark})")
    int insertJob(SysJob job);

    @Update("UPDATE sys_job SET job_name = #{jobName}, job_group = #{jobGroup}, invoke_target = #{invokeTarget}, " +
            "cron_expression = #{cronExpression}, misfire_policy = #{misfirePolicy}, concurrent = #{concurrent}, " +
            "status = #{status}, update_by = #{updateBy}, update_time = NOW(), remark = #{remark} " +
            "WHERE job_id = #{jobId}")
    int updateJob(SysJob job);

    @Update("UPDATE sys_job SET status = #{status}, update_by = #{updateBy}, update_time = NOW() " +
            "WHERE job_id = #{jobId}")
    int updateJobStatus(@Param("jobId") Long jobId, @Param("status") String status, @Param("updateBy") String updateBy);

    @Delete("DELETE FROM sys_job WHERE job_id = #{jobId}")
    int deleteJobById(@Param("jobId") Long jobId);
}
