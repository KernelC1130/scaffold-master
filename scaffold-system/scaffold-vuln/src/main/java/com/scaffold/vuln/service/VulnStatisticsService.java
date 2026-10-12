package com.scaffold.vuln.service;

import com.scaffold.vuln.mapper.VulnStatisticsMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 工作台安全态势：指标汇总与分布聚合，纯透传无业务规则。 */
@Service
public class VulnStatisticsService {

    private final VulnStatisticsMapper mapper;

    public VulnStatisticsService(VulnStatisticsMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> summary() {
        return Map.of(
                "totalAssets", mapper.countAssets(),        // 总资产数
                "openVulns", mapper.countOpenVulns(),       // 未闭合漏洞（新发现+修复中）
                "highRiskOpen", mapper.countHighRiskOpen(), // 高风险未闭合漏洞
                "weekTasks", mapper.countTasksThisWeek()    // 本周新工单
        );
    }

    public List<Map<String, Object>> vulnByRiskLevel() {
        return mapper.selectVulnByRiskLevel();
    }

    public List<Map<String, Object>> vulnByMonth() {
        return mapper.selectVulnByMonth();
    }

    public List<Map<String, Object>> topRiskAssets() {
        return mapper.selectTopRiskAssets();
    }
}
