package com.scaffold.vuln.controller;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.vuln.domain.VulnAssetRow;
import com.scaffold.vuln.domain.dto.AssetWriteRequest;
import com.scaffold.vuln.service.VulnAssetService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/** 资产管理接口（/api 前缀由 scaffold.api-prefix 统一约定）。 */
@RestController
@RequestMapping("/api/vuln/assets")
public class VulnAssetController {

    private final VulnAssetService service;

    public VulnAssetController(VulnAssetService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("vuln:asset:list")
    public R<PageResult<VulnAssetRow>> assets(
            PageQuery query,
            @RequestParam(required = false) String assetIp,
            @RequestParam(required = false) String assetName,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String status
    ) {
        return R.success(service.assets(query, assetIp, assetName, riskLevel, status));
    }

    @GetMapping("/export")
    @RequiresPermission("vuln:asset:list") // 导出权限复用 list（scaffold 约定）
    public ResponseEntity<String> exportAssets(
            @RequestParam(required = false) String assetIp,
            @RequestParam(required = false) String assetName,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String status
    ) {
        return csv("assets.csv", service.exportAssets(assetIp, assetName, riskLevel, status));
    }

    @GetMapping("/{id}")
    @RequiresPermission("vuln:asset:list") // 详情复用 list 权限
    public R<VulnAssetRow> asset(@PathVariable Long id) {
        return R.success(service.asset(id));
    }

    @PostMapping
    @RequiresPermission("vuln:asset:add")
    @OperLog(module = "资产管理", action = "新增")
    public R<Long> createAsset(@RequestBody AssetWriteRequest request) {
        return R.success(service.createAsset(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("vuln:asset:edit")
    @OperLog(module = "资产管理", action = "修改")
    public R<Void> updateAsset(@PathVariable Long id, @RequestBody AssetWriteRequest request) {
        service.updateAsset(id, request);
        return R.success(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("vuln:asset:remove")
    @OperLog(module = "资产管理", action = "删除")
    public R<Void> deleteAsset(@PathVariable Long id) {
        service.deleteAsset(id);
        return R.success(null);
    }

    private ResponseEntity<String> csv(String filename, String content) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content);
    }
}
