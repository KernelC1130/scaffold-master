package com.scaffold.vuln.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.scaffold.vuln.domain.VulnAssetRow;
import com.scaffold.vuln.domain.VulnRepairTaskRow;
import com.scaffold.vuln.domain.VulnVulnerabilityRow;
import com.scaffold.vuln.mapper.VulnAssetMapper;
import com.scaffold.vuln.mapper.VulnRepairTaskMapper;
import com.scaffold.vuln.mapper.VulnStatisticsMapper;
import com.scaffold.vuln.mapper.VulnVulnerabilityMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * AI 助手业务工具集（供大模型 Function Calling 调用，内部走 vuln 模块 Mapper）。
 * 返回给模型的字段经过精炼与中文标签翻译，列表类工具限制条数以防上下文膨胀。
 */
@Component
public class VulnTools {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int PROGRESS_MAX = 80;

    private static final Map<String, String> RISK_LABELS = Map.of("0", "低", "1", "中", "2", "高");
    private static final Map<String, String> ASSET_STATUS_LABELS = Map.of("0", "正常", "1", "停用");
    private static final Map<String, String> VULN_STATUS_LABELS = Map.of("0", "新发现", "1", "修复中", "2", "已修复", "3", "已关闭");
    private static final Map<String, String> TASK_STATUS_LABELS = Map.of("0", "待处理", "1", "处理中", "2", "已修复", "3", "已验证", "4", "已关闭");
    private static final Map<String, String> PRIORITY_LABELS = Map.of("0", "低", "1", "中", "2", "高", "3", "紧急");

    private final VulnVulnerabilityMapper vulnMapper;
    private final VulnAssetMapper assetMapper;
    private final VulnRepairTaskMapper taskMapper;
    private final VulnStatisticsMapper statMapper;

    public VulnTools(VulnVulnerabilityMapper vulnMapper, VulnAssetMapper assetMapper,
                     VulnRepairTaskMapper taskMapper, VulnStatisticsMapper statMapper) {
        this.vulnMapper = vulnMapper;
        this.assetMapper = assetMapper;
        this.taskMapper = taskMapper;
        this.statMapper = statMapper;
    }

    /** 平台总览：一句话回答「现在什么情况」的四个关键读数 */
    @Tool(description = "平台总览：资产总数、未闭合漏洞数、高危未闭合漏洞数、本周新工单数。用户问平台/系统整体情况时先调用此工具")
    public Map<String, Object> platformSummary() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("资产总数", statMapper.countAssets());
        r.put("未闭合漏洞数", statMapper.countOpenVulns());
        r.put("高危未闭合漏洞数", statMapper.countHighRiskOpen());
        r.put("本周新工单数", statMapper.countTasksThisWeek());
        return r;
    }

    /** 漏洞列表（可按风险/状态过滤） */
    @Tool(description = "查询漏洞列表（含所属资产名称、IP），可按风险等级、漏洞状态过滤，都不传时查全部")
    public Map<String, Object> listVulns(
            @ToolParam(description = "风险等级代码：0=低 1=中 2=高，不过滤传空串", required = false) String riskLevel,
            @ToolParam(description = "漏洞状态代码：0=新发现 1=修复中 2=已修复 3=已关闭，不过滤传空串", required = false) String vulnStatus,
            @ToolParam(description = "返回条数上限，默认 20，最大 50", required = false) Integer limit) {
        int n = normalizeLimit(limit);
        long total = vulnMapper.countVulnerabilities(null, null, null, riskLevel, vulnStatus, null);
        List<VulnVulnerabilityRow> rows = vulnMapper.selectVulnerabilities(
                null, null, null, riskLevel, vulnStatus, null, 0, n);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("总数", total);
        r.put("列表", rows.stream().map(this::vulnView).collect(Collectors.toList()));
        if (total > n) r.put("说明", "共 " + total + " 条，仅显示前 " + n + " 条");
        return r;
    }

    /** 漏洞统计（按风险等级 / 按状态，中文标签） */
    @Tool(description = "统计漏洞数量：按风险等级汇总、按漏洞状态汇总")
    public Map<String, Object> vulnStatistics() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("按风险等级", translateCounts(statMapper.selectVulnByRiskLevel(), RISK_LABELS));
        List<VulnVulnerabilityRow> all = vulnMapper.selectVulnerabilities(
                null, null, null, null, null, null, 0, MAX_LIMIT * 4);
        r.put("按状态", groupCount(all, VulnVulnerabilityRow::getVulnStatus, VULN_STATUS_LABELS));
        return r;
    }

    /** 资产列表（可按风险/状态过滤） */
    @Tool(description = "查询资产列表（IP、名称、负责人、风险等级、状态、备注），可按风险等级、状态过滤，都不传时查全部")
    public Map<String, Object> listAssets(
            @ToolParam(description = "资产风险等级代码：0=低 1=中 2=高，不过滤传空串", required = false) String riskLevel,
            @ToolParam(description = "资产状态代码：0=正常 1=停用，不过滤传空串", required = false) String status,
            @ToolParam(description = "返回条数上限，默认 20，最大 50", required = false) Integer limit) {
        int n = normalizeLimit(limit);
        long total = assetMapper.countAssets(null, null, riskLevel, status);
        List<VulnAssetRow> rows = assetMapper.selectAssets(null, null, riskLevel, status, 0, n);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("总数", total);
        r.put("列表", rows.stream().map(this::assetView).collect(Collectors.toList()));
        if (total > n) r.put("说明", "共 " + total + " 条，仅显示前 " + n + " 条");
        return r;
    }

    /** 资产统计（总数 / 按风险等级 / 按状态） */
    @Tool(description = "统计资产数量：总数、按资产风险等级汇总、按资产状态汇总")
    public Map<String, Object> assetStatistics() {
        List<VulnAssetRow> all = assetMapper.selectAssets(null, null, null, null, 0, MAX_LIMIT);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("总数", assetMapper.countAssets(null, null, null, null));
        r.put("按风险等级", groupCount(all, VulnAssetRow::getRiskLevel, RISK_LABELS));
        r.put("按状态", groupCount(all, VulnAssetRow::getStatus, ASSET_STATUS_LABELS));
        return r;
    }

    /** 修复工单列表（可按状态/优先级过滤） */
    @Tool(description = "查询修复工单列表（含关联漏洞标题、处理人、验证人、计划/实际完成时间、进度说明），可按工单状态、优先级过滤，都不传时查全部")
    public Map<String, Object> listRepairTasks(
            @ToolParam(description = "工单状态代码：0=待处理 1=处理中 2=已修复 3=已验证 4=已关闭，不过滤传空串", required = false) String taskStatus,
            @ToolParam(description = "优先级代码：0=低 1=中 2=高 3=紧急，不过滤传空串", required = false) String priority,
            @ToolParam(description = "返回条数上限，默认 20，最大 50", required = false) Integer limit) {
        int n = normalizeLimit(limit);
        long total = taskMapper.countTasks(null, null, taskStatus, priority, null);
        List<VulnRepairTaskRow> rows = taskMapper.selectTasks(null, null, taskStatus, priority, null, 0, n);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("总数", total);
        r.put("列表", rows.stream().map(this::taskView).collect(Collectors.toList()));
        if (total > n) r.put("说明", "共 " + total + " 条，仅显示前 " + n + " 条");
        return r;
    }

    /** 工单统计（总数 / 按状态 / 按优先级） */
    @Tool(description = "统计修复工单数量：总数、按工单状态汇总、按优先级汇总")
    public Map<String, Object> repairTaskStatistics() {
        List<VulnRepairTaskRow> all = taskMapper.selectTasks(null, null, null, null, null, 0, MAX_LIMIT);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("总数", taskMapper.countTasks(null, null, null, null, null));
        r.put("按状态", groupCount(all, VulnRepairTaskRow::getTaskStatus, TASK_STATUS_LABELS));
        r.put("按优先级", groupCount(all, VulnRepairTaskRow::getPriority, PRIORITY_LABELS));
        return r;
    }

    /** 高风险资产 TOP5（关联未闭合漏洞数最多） */
    @Tool(description = "查询高风险资产 TOP5：风险等级为高的资产，按关联未闭合漏洞数量排序")
    public List<Map<String, Object>> topRiskAssets() {
        return statMapper.selectTopRiskAssets();
    }

    /* ── 视图裁剪：只给模型业务字段，审计/备注等排除 ── */

    private Map<String, Object> vulnView(VulnVulnerabilityRow row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("漏洞ID", row.getId());
        m.put("标题", row.getVulnTitle());
        if (row.getCveNo() != null && !row.getCveNo().isEmpty()) m.put("CVE", row.getCveNo());
        m.put("风险等级", label(row.getRiskLevel(), RISK_LABELS));
        m.put("状态", label(row.getVulnStatus(), VULN_STATUS_LABELS));
        m.put("所属资产", row.getAssetName() + "（" + row.getAssetIp() + "）");
        if (row.getFoundTime() != null) m.put("发现日期", row.getFoundTime().toString());
        return m;
    }

    private Map<String, Object> assetView(VulnAssetRow row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("资产ID", row.getId());
        m.put("IP", row.getAssetIp());
        m.put("名称", row.getAssetName());
        m.put("负责人", row.getOwner() == null || row.getOwner().isEmpty() ? "未指定" : row.getOwner());
        m.put("风险等级", label(row.getRiskLevel(), RISK_LABELS));
        m.put("状态", label(row.getStatus(), ASSET_STATUS_LABELS));
        if (row.getRemark() != null && !row.getRemark().isEmpty()) m.put("备注", row.getRemark());
        return m;
    }

    private Map<String, Object> taskView(VulnRepairTaskRow row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("工单ID", row.getId());
        m.put("漏洞标题", row.getVulnTitle());
        m.put("状态", label(row.getTaskStatus(), TASK_STATUS_LABELS));
        m.put("优先级", label(row.getPriority(), PRIORITY_LABELS));
        if (row.getHandlerName() != null && !row.getHandlerName().isEmpty()) m.put("处理人", row.getHandlerName());
        if (row.getVerifierName() != null && !row.getVerifierName().isEmpty()) m.put("验证人", row.getVerifierName());
        if (row.getPlanTime() != null) m.put("计划完成", row.getPlanTime().toString());
        if (row.getFinishTime() != null) m.put("实际完成", row.getFinishTime());
        if (row.getProgressDesc() != null && !row.getProgressDesc().isEmpty()) {
            String desc = row.getProgressDesc();
            m.put("进度说明", desc.length() > PROGRESS_MAX ? desc.substring(0, PROGRESS_MAX) + "…" : desc);
        }
        return m;
    }

    /* ── 通用：代码值分组计数并翻译为中文标签；统计行的 name 列同样翻译 ── */

    private <T> List<Map<String, Object>> groupCount(List<T> list, Function<T, String> getter, Map<String, String> labels) {
        Map<String, Long> counts = list.stream()
                .collect(Collectors.groupingBy(v -> v == null || getter.apply(v) == null ? "未知" : getter.apply(v), Collectors.counting()));
        return counts.entrySet().stream()
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", label(e.getKey(), labels));
                    row.put("value", e.getValue());
                    return row;
                })
                .collect(Collectors.toList());
    }

    /** 统计 Mapper 返回的 name 是代码值，翻译为中文标签 */
    private List<Map<String, Object>> translateCounts(List<Map<String, Object>> rows, Map<String, String> labels) {
        return rows.stream()
                .map(row -> {
                    String code = String.valueOf(row.get("name"));
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("name", label(code, labels));
                    out.put("value", row.get("value"));
                    return out;
                })
                .collect(Collectors.toList());
    }

    private static String label(String code, Map<String, String> labels) {
        if (code == null || code.isEmpty()) return "未知";
        return labels.getOrDefault(code, code);
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null) return DEFAULT_LIMIT;
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
