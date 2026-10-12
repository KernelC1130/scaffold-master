package com.scaffold.vuln.controller;

import com.scaffold.common.domain.R;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.vuln.service.VulnStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 工作台安全态势接口（只读聚合）。 */
@RestController
@RequestMapping("/api/vuln/statistics")
public class VulnStatisticsController {

    private final VulnStatisticsService service;

    public VulnStatisticsController(VulnStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @RequiresPermission("vuln:vulnerability:list")
    public R<Map<String, Object>> summary() {
        return R.success(service.summary());
    }

    @GetMapping("/vulnByRiskLevel")
    @RequiresPermission("vuln:vulnerability:list")
    public R<List<Map<String, Object>>> vulnByRiskLevel() {
        return R.success(service.vulnByRiskLevel());
    }

    @GetMapping("/vulnByMonth")
    @RequiresPermission("vuln:vulnerability:list")
    public R<List<Map<String, Object>>> vulnByMonth() {
        return R.success(service.vulnByMonth());
    }

    @GetMapping("/topRiskAssets")
    @RequiresPermission("vuln:vulnerability:list")
    public R<List<Map<String, Object>>> topRiskAssets() {
        return R.success(service.topRiskAssets());
    }
}
