package com.scaffold.vuln.mapper;

import com.scaffold.vuln.domain.VulnRepairTaskRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 修复工单表访问（vuln_repair_task）。
 * 列表 JOIN 漏洞（v.）与资产（a.）带展示字段，两次 JOIN sys_user（uh 负责人 / uv 验证人）带昵称。
 */
public interface VulnRepairTaskMapper {

    String JOIN = """
            FROM vuln_repair_task t
                     LEFT JOIN vuln_vulnerability v ON t.vuln_id = v.id
                     LEFT JOIN vuln_asset a ON v.asset_id = a.id
                     LEFT JOIN sys_user uh ON t.handler_id = uh.id AND uh.deleted = 0
                     LEFT JOIN sys_user uv ON t.verifier_id = uv.id AND uv.deleted = 0
            """;

    @Select("""
            <script>
            SELECT COUNT(*)
            """ + JOIN + """
            <where>
                <if test="vulnId != null">AND t.vuln_id = #{vulnId}</if>
                <if test="vulnTitle != null and vulnTitle != ''">AND v.vuln_title LIKE CONCAT('%', #{vulnTitle}, '%')</if>
                <if test="taskStatus != null and taskStatus != ''">AND t.task_status = #{taskStatus}</if>
                <if test="priority != null and priority != ''">AND t.priority = #{priority}</if>
                <if test="handlerId != null">AND t.handler_id = #{handlerId}</if>
            </where>
            </script>
            """)
    long countTasks(
            @Param("vulnId") Long vulnId,
            @Param("vulnTitle") String vulnTitle,
            @Param("taskStatus") String taskStatus,
            @Param("priority") String priority,
            @Param("handlerId") Long handlerId
    );

    @Select("""
            <script>
            SELECT t.id,
                   t.vuln_id,
                   t.handler_id,
                   t.task_status,
                   t.priority,
                   t.progress_desc,
                   t.plan_time,
                   DATE_FORMAT(t.finish_time, '%Y-%m-%d %H:%i:%s') AS finish_time,
                   t.verifier_id,
                   t.verify_remark,
                   t.remark,
                   v.vuln_title,
                   v.risk_level        AS vulnRiskLevel,
                   a.asset_name,
                   a.asset_ip,
                   uh.nickname          AS handlerName,
                   uv.nickname          AS verifierName,
                   DATE_FORMAT(t.create_time, '%Y-%m-%d %H:%i:%s') AS create_time,
                   DATE_FORMAT(t.update_time, '%Y-%m-%d %H:%i:%s') AS update_time
            """ + JOIN + """
            <where>
                <if test="vulnId != null">AND t.vuln_id = #{vulnId}</if>
                <if test="vulnTitle != null and vulnTitle != ''">AND v.vuln_title LIKE CONCAT('%', #{vulnTitle}, '%')</if>
                <if test="taskStatus != null and taskStatus != ''">AND t.task_status = #{taskStatus}</if>
                <if test="priority != null and priority != ''">AND t.priority = #{priority}</if>
                <if test="handlerId != null">AND t.handler_id = #{handlerId}</if>
            </where>
            ORDER BY t.id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<VulnRepairTaskRow> selectTasks(
            @Param("vulnId") Long vulnId,
            @Param("vulnTitle") String vulnTitle,
            @Param("taskStatus") String taskStatus,
            @Param("priority") String priority,
            @Param("handlerId") Long handlerId,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    @Select("""
            SELECT t.id,
                   t.vuln_id,
                   t.handler_id,
                   t.task_status,
                   t.priority,
                   t.progress_desc,
                   t.plan_time,
                   DATE_FORMAT(t.finish_time, '%Y-%m-%d %H:%i:%s') AS finish_time,
                   t.verifier_id,
                   t.verify_remark,
                   t.remark,
                   v.vuln_title,
                   v.risk_level        AS vulnRiskLevel,
                   a.asset_name,
                   a.asset_ip,
                   uh.nickname          AS handlerName,
                   uv.nickname          AS verifierName,
                   DATE_FORMAT(t.create_time, '%Y-%m-%d %H:%i:%s') AS create_time,
                   DATE_FORMAT(t.update_time, '%Y-%m-%d %H:%i:%s') AS update_time
            """ + JOIN + """
            WHERE t.id = #{id}
            """)
    VulnRepairTaskRow selectTaskById(@Param("id") Long id);

    /** 到期提醒扫描：未完成工单（0待处理/1处理中）且计划完成日早于上限的，带漏洞与资产展示字段。 */
    @Select("""
            SELECT t.id,
                   t.vuln_id,
                   t.handler_id,
                   t.task_status,
                   DATE(t.plan_time) AS plan_time,
                   v.vuln_title,
                   a.asset_name,
                   a.asset_ip,
                   uh.nickname AS handlerName
            FROM vuln_repair_task t
                     LEFT JOIN vuln_vulnerability v ON t.vuln_id = v.id
                     LEFT JOIN vuln_asset a ON v.asset_id = a.id
                     LEFT JOIN sys_user uh ON t.handler_id = uh.id AND uh.deleted = 0
            WHERE t.task_status IN ('0', '1')
              AND t.handler_id IS NOT NULL
              AND t.plan_time IS NOT NULL
              AND t.plan_time < #{limit}
            ORDER BY t.plan_time ASC, t.id ASC
            """)
    List<VulnRepairTaskRow> selectRemindTasks(@Param("limit") LocalDate limit);

    @Insert("""
            INSERT INTO vuln_repair_task (vuln_id, handler_id, task_status, priority, progress_desc, plan_time, remark, create_by, update_by)
            VALUES (#{row.vulnId}, #{row.handlerId}, '0', #{row.priority}, #{row.progressDesc}, #{row.planTime}, #{row.remark}, #{operator}, #{operator})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertTask(@Param("row") VulnRepairTaskRow row, @Param("operator") String operator);

    @Update("""
            UPDATE vuln_repair_task
            SET vuln_id       = #{row.vulnId},
                handler_id    = #{row.handlerId},
                priority      = #{row.priority},
                progress_desc = #{row.progressDesc},
                plan_time     = #{row.planTime},
                remark        = #{row.remark},
                update_by     = #{operator}
            WHERE id = #{id}
            """)
    int updateTask(@Param("id") Long id, @Param("row") VulnRepairTaskRow row, @Param("operator") String operator);

    @Update("""
            UPDATE vuln_repair_task
            SET task_status   = #{taskStatus},
                finish_time   = #{finishTime},
                verifier_id   = #{verifierId},
                verify_remark = #{verifyRemark},
                progress_desc = COALESCE(#{progressDesc}, progress_desc),
                update_by     = #{operator}
            WHERE id = #{id}
            """)
    int transitTask(
            @Param("id") Long id,
            @Param("taskStatus") String taskStatus,
            @Param("finishTime") java.time.LocalDateTime finishTime,
            @Param("verifierId") Long verifierId,
            @Param("verifyRemark") String verifyRemark,
            @Param("progressDesc") String progressDesc,
            @Param("operator") String operator
    );

    /** 漏洞下工单状态聚合（联动漏洞状态）：totalCount/pendingCount/processingCount/fixedCount/verifiedCount/closedCount。 */
    @Select("""
            SELECT COUNT(*)                                                                 AS totalCount,
                   COALESCE(SUM(CASE WHEN task_status = '0' THEN 1 ELSE 0 END), 0)          AS pendingCount,
                   COALESCE(SUM(CASE WHEN task_status = '1' THEN 1 ELSE 0 END), 0)          AS processingCount,
                   COALESCE(SUM(CASE WHEN task_status = '2' THEN 1 ELSE 0 END), 0)          AS fixedCount,
                   COALESCE(SUM(CASE WHEN task_status = '3' THEN 1 ELSE 0 END), 0)          AS verifiedCount,
                   COALESCE(SUM(CASE WHEN task_status = '4' THEN 1 ELSE 0 END), 0)          AS closedCount
            FROM vuln_repair_task
            WHERE vuln_id = #{vulnId}
            """)
    Map<String, Object> selectStatusSummaryByVulnId(@Param("vulnId") Long vulnId);

    @Delete("DELETE FROM vuln_repair_task WHERE id = #{id}")
    int deleteTask(@Param("id") Long id);

    /** 校验用户存在且启用（负责人/验证人），跨模块直查 sys_user，避免依赖 system 模块。 */
    @Select("SELECT COUNT(*) FROM sys_user WHERE id = #{userId} AND status = 1 AND deleted = 0")
    long countActiveUser(@Param("userId") Long userId);

    /** 登录名 → 用户 ID（复测写入 verifierId 与自复测校验用）。 */
    @Select("SELECT id FROM sys_user WHERE username = #{username} AND deleted = 0")
    Long selectUserIdByUsername(@Param("username") String username);
}
