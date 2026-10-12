package com.scaffold.vuln.service;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.security.PermissionResolver;
import com.scaffold.vuln.domain.VulnRepairTaskRow;
import com.scaffold.vuln.domain.VulnVulnerabilityRow;
import com.scaffold.vuln.domain.dto.TaskActionRequest;
import com.scaffold.vuln.domain.dto.TaskWriteRequest;
import com.scaffold.vuln.mapper.VulnRepairTaskMapper;
import com.scaffold.vuln.mapper.VulnVulnerabilityMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 修复工单业务：CRUD + 状态机动作 + 漏洞/资产联动。
 * 业务规则（迁移自原漏洞平台）：
 * - 状态机 0待处理 →1处理中 →2已修复 →{3已验证 | 1处理中(打回)} →4已关闭；
 * - 接单/提交修复须负责人本人或具备工单编辑权限；复测须非负责人本人（复测独立性），验证意见必填；
 * - 提交修复清空上轮验证意见与验证人；状态 2/3/4 时实际完成时间必填；
 * - 每次状态变化后按工单聚合重算漏洞状态，并联动重算资产风险。
 */
@Service
public class VulnRepairTaskService {

    private static final List<String> PRIORITIES = List.of("0", "1", "2", "3");
    private static final int EXPORT_LIMIT = 5000;

    private final VulnRepairTaskMapper mapper;
    private final VulnVulnerabilityMapper vulnerabilityMapper;
    private final VulnVulnerabilityService vulnerabilityService;
    private final PermissionResolver permissionResolver;

    public VulnRepairTaskService(
            VulnRepairTaskMapper mapper,
            VulnVulnerabilityMapper vulnerabilityMapper,
            VulnVulnerabilityService vulnerabilityService,
            PermissionResolver permissionResolver
    ) {
        this.mapper = mapper;
        this.vulnerabilityMapper = vulnerabilityMapper;
        this.vulnerabilityService = vulnerabilityService;
        this.permissionResolver = permissionResolver;
    }

    public PageResult<VulnRepairTaskRow> tasks(
            PageQuery query, Long vulnId, String vulnTitle, String taskStatus, String priority, Long handlerId
    ) {
        Page page = pageOf(query);
        long total = mapper.countTasks(vulnId, vulnTitle, taskStatus, priority, handlerId);
        return PageResult.of(
                mapper.selectTasks(vulnId, vulnTitle, taskStatus, priority, handlerId, page.offset(), page.pageSize()),
                total, page.pageNum(), page.pageSize());
    }

    public String exportTasks(Long vulnId, String vulnTitle, String taskStatus, String priority, Long handlerId) {
        List<VulnRepairTaskRow> rows = mapper.selectTasks(vulnId, vulnTitle, taskStatus, priority, handlerId, 0, EXPORT_LIMIT);
        List<List<Object>> data = rows.stream()
                .map(row -> csvRow(
                        row.getVulnTitle(),
                        defaultString(row.getAssetName()) + (row.getAssetIp() == null ? "" : "（" + row.getAssetIp() + "）"),
                        riskLevelText(row.getVulnRiskLevel()),
                        taskStatusText(row.getTaskStatus()),
                        priorityText(row.getPriority()),
                        defaultString(row.getHandlerName()),
                        row.getPlanTime() == null ? "" : row.getPlanTime().toString(),
                        row.getFinishTime() == null ? "" : row.getFinishTime().toString(),
                        defaultString(row.getVerifierName()),
                        defaultString(row.getVerifyRemark())
                ))
                .toList();
        return csv(List.of("漏洞标题", "所属资产", "漏洞风险", "工单状态", "优先级", "负责人", "计划完成", "实际完成", "验证人", "验证意见"), data);
    }

    public VulnRepairTaskRow task(Long id) {
        VulnRepairTaskRow row = mapper.selectTaskById(id);
        if (row == null) {
            throw new BusinessException("修复工单不存在");
        }
        return row;
    }

    @Transactional
    public Long createTask(TaskWriteRequest request) {
        VulnRepairTaskRow row = new VulnRepairTaskRow();
        applyWrite(row, request);
        mapper.insertTask(row, currentUsername());
        syncVulnerabilityStatus(row.getVulnId());
        return row.getId();
    }

    @Transactional
    public void updateTask(Long id, TaskWriteRequest request) {
        VulnRepairTaskRow existing = requireTask(id);
        VulnRepairTaskRow row = new VulnRepairTaskRow();
        applyWrite(row, request);
        if (mapper.updateTask(id, row, currentUsername()) == 0) {
            throw new BusinessException("修复工单不存在");
        }
        syncVulnerabilityStatus(existing.getVulnId());
        syncVulnerabilityStatus(row.getVulnId());
    }

    @Transactional
    public void deleteTask(Long id) {
        VulnRepairTaskRow existing = requireTask(id);
        mapper.deleteTask(id);
        syncVulnerabilityStatus(existing.getVulnId());
    }

    /** 接单：0待处理 → 1处理中（负责人本人或有工单编辑权限）。 */
    @Transactional
    public void acceptTask(Long id) {
        VulnRepairTaskRow task = requireTask(id);
        requireStatus(task, "0", "接单");
        checkHandlerBoundary(task, "只有负责人本人可以接单");
        transit(id, "1", null, null, null, null);
    }

    /** 提交修复：1处理中 → 2已修复（负责人本人或有编辑权限），清空上轮验证意见与验证人。 */
    @Transactional
    public void fixTask(Long id, TaskActionRequest request) {
        VulnRepairTaskRow task = requireTask(id);
        requireStatus(task, "1", "提交修复");
        checkHandlerBoundary(task, "只有负责人本人可以提交修复");
        transit(id, "2", requiredFinishTime(request), null, "", optionalProgress(request));
    }

    /** 复测：2已修复 → 3已验证（通过）| 1处理中（打回）。验证人不能是负责人本人，验证意见必填。 */
    @Transactional
    public void verifyTask(Long id, TaskActionRequest request) {
        VulnRepairTaskRow task = requireTask(id);
        requireStatus(task, "2", "复测");
        Long operatorId = requireCurrentUserId();
        if (operatorId.equals(task.getHandlerId())) {
            throw new BusinessException("复测人不能是工单负责人本人");
        }
        String remark = required(request.getVerifyRemark(), "验证意见不能为空");
        if (remark.length() > 500) {
            throw new BusinessException("验证意见不能超过 500 字");
        }
        boolean pass = Boolean.TRUE.equals(request.getPass());
        transit(id, pass ? "3" : "1", pass ? requiredFinishTime(request) : null, operatorId, remark, null);
    }

    /** 关闭：3已验证 → 4已关闭。 */
    @Transactional
    public void closeTask(Long id) {
        VulnRepairTaskRow task = requireTask(id);
        requireStatus(task, "3", "关闭");
        transit(id, "4", null, null, null, null);
    }

    private void transit(Long id, String status, LocalDateTime finishTime, Long verifierId, String verifyRemark, String progressDesc) {
        VulnRepairTaskRow task = requireTask(id);
        if (mapper.transitTask(id, status, finishTime, verifierId, verifyRemark, progressDesc, currentUsername()) == 0) {
            throw new BusinessException("修复工单不存在");
        }
        syncVulnerabilityStatus(task.getVulnId());
    }

    /** 漏洞状态 = 工单聚合：全待处理→新发现；全关闭→已关闭；有待/处理中→修复中；有已修复/已验证→已修复。 */
    private void syncVulnerabilityStatus(Long vulnId) {
        Map<String, Object> summary = mapper.selectStatusSummaryByVulnId(vulnId);
        if (summary == null) {
            return; // 漏洞可能已被删除
        }
        long total = ((Number) summary.getOrDefault("totalCount", 0)).longValue();
        long pending = ((Number) summary.getOrDefault("pendingCount", 0)).longValue();
        long processing = ((Number) summary.getOrDefault("processingCount", 0)).longValue();
        long fixed = ((Number) summary.getOrDefault("fixedCount", 0)).longValue();
        long verified = ((Number) summary.getOrDefault("verifiedCount", 0)).longValue();
        long closed = ((Number) summary.getOrDefault("closedCount", 0)).longValue();

        String status;
        if (total == 0 || pending == total) {
            status = "0";
        } else if (closed == total) {
            status = "3";
        } else if (pending + processing > 0) {
            status = "1";
        } else if (fixed + verified > 0) {
            status = "2";
        } else {
            status = "0";
        }

        VulnVulnerabilityRow vuln = vulnerabilityMapper.selectVulnerabilityById(vulnId);
        if (vuln == null) {
            return;
        }
        if (!status.equals(vuln.getVulnStatus())) {
            vulnerabilityMapper.updateStatusById(vulnId, status, currentUsername());
        }
        vulnerabilityService.recalculateAssetRisk(vuln.getAssetId());
    }

    private void applyWrite(VulnRepairTaskRow row, TaskWriteRequest request) {
        if (request.getVulnId() == null) {
            throw new BusinessException("请选择关联漏洞");
        }
        VulnVulnerabilityRow vuln = vulnerabilityMapper.selectVulnerabilityById(request.getVulnId());
        if (vuln == null) {
            throw new BusinessException("关联漏洞不存在");
        }
        row.setVulnId(request.getVulnId());
        if (request.getHandlerId() == null) {
            throw new BusinessException("请选择负责人");
        }
        if (mapper.countActiveUser(request.getHandlerId()) == 0) {
            throw new BusinessException("负责人不存在或已停用");
        }
        row.setHandlerId(request.getHandlerId());
        String priority = request.getPriority() == null ? "1" : request.getPriority();
        if (!PRIORITIES.contains(priority)) {
            throw new BusinessException("优先级取值不合法");
        }
        row.setPriority(priority);
        String progress = trimToNull(request.getProgressDesc());
        if (progress != null && progress.length() > 500) {
            throw new BusinessException("修复进度说明不能超过 500 字");
        }
        row.setProgressDesc(progress);
        row.setPlanTime(parseDate(request.getPlanTime(), "计划完成时间"));
        String remark = trimToNull(request.getRemark());
        if (remark != null && remark.length() > 500) {
            throw new BusinessException("备注不能超过 500 字");
        }
        row.setRemark(remark);
    }

    private void requireStatus(VulnRepairTaskRow task, String expected, String action) {
        if (!expected.equals(task.getTaskStatus())) {
            throw new BusinessException("当前状态不允许" + action + "（仅" + statusText(expected) + "状态可" + action + "）");
        }
    }

    private void checkHandlerBoundary(VulnRepairTaskRow task, String message) {
        Long operatorId = requireCurrentUserId();
        if (!operatorId.equals(task.getHandlerId())
                && !permissionResolver.hasPermission(currentUsername(), "vuln:repair:edit")) {
            throw new BusinessException(message);
        }
    }

    private Long requireCurrentUserId() {
        Long userId = mapper.selectUserIdByUsername(currentUsername());
        if (userId == null) {
            throw new BusinessException("当前登录用户不存在");
        }
        return userId;
    }

    private VulnRepairTaskRow requireTask(Long id) {
        VulnRepairTaskRow row = mapper.selectTaskById(id);
        if (row == null) {
            throw new BusinessException("修复工单不存在");
        }
        return row;
    }

    private LocalDateTime requiredFinishTime(TaskActionRequest request) {
        String trimmed = trimToNull(request.getFinishTime());
        if (trimmed == null) {
            throw new BusinessException("实际完成时间不能为空");
        }
        try {
            return LocalDateTime.parse(trimmed.length() == 16 ? trimmed + ":00" : trimmed);
        } catch (DateTimeParseException e) {
            throw new BusinessException("实际完成时间格式不合法");
        }
    }

    private String optionalProgress(TaskActionRequest request) {
        String progress = trimToNull(request.getProgressDesc());
        if (progress != null && progress.length() > 500) {
            throw new BusinessException("修复进度说明不能超过 500 字");
        }
        return progress;
    }

    private LocalDate parseDate(String value, String label) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException e) {
            throw new BusinessException(label + "格式应为 yyyy-MM-dd");
        }
    }

    private String riskLevelText(String level) {
        return switch (level == null ? "" : level) {
            case "0" -> "低";
            case "1" -> "中";
            case "2" -> "高";
            default -> "";
        };
    }

    private String taskStatusText(String status) {
        return switch (status == null ? "" : status) {
            case "0" -> "待处理";
            case "1" -> "处理中";
            case "2" -> "已修复";
            case "3" -> "已验证";
            case "4" -> "已关闭";
            default -> "";
        };
    }

    private String priorityText(String priority) {
        return switch (priority == null ? "" : priority) {
            case "0" -> "低";
            case "1" -> "中";
            case "2" -> "高";
            case "3" -> "紧急";
            default -> "";
        };
    }

    private String statusText(String status) {
        return taskStatusText(status);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String required(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : String.valueOf(authentication.getPrincipal());
    }

    private record Page(int pageNum, int pageSize, int offset) {
    }

    private Page pageOf(PageQuery query) {
        int pageNum = query.pageNum() == null || query.pageNum() < 1 ? 1 : query.pageNum();
        int pageSize = query.pageSize() == null || query.pageSize() < 1 ? 10 : Math.min(query.pageSize(), 100);
        return new Page(pageNum, pageSize, (pageNum - 1) * pageSize);
    }

    private String csv(List<String> headers, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers)).append('\n');
        for (List<Object> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(csvCell(row.get(i)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private List<Object> csvRow(Object... values) {
        return Arrays.asList(values);
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains("\"")) {
            text = text.replace("\"", "\"\"");
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text + "\"";
        }
        return text;
    }
}
