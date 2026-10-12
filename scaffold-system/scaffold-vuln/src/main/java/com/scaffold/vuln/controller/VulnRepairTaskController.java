package com.scaffold.vuln.controller;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.vuln.domain.VulnRepairTaskRow;
import com.scaffold.vuln.domain.dto.TaskActionRequest;
import com.scaffold.vuln.domain.dto.TaskWriteRequest;
import com.scaffold.vuln.service.VulnRepairTaskService;
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

/** 修复工单接口：CRUD + 语义化状态动作（accept / fix / verify / close）。 */
@RestController
@RequestMapping("/api/vuln/repair-tasks")
public class VulnRepairTaskController {

    private final VulnRepairTaskService service;

    public VulnRepairTaskController(VulnRepairTaskService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("vuln:repair:list")
    public R<PageResult<VulnRepairTaskRow>> tasks(
            PageQuery query,
            @RequestParam(required = false) Long vulnId,
            @RequestParam(required = false) String vulnTitle,
            @RequestParam(required = false) String taskStatus,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long handlerId
    ) {
        return R.success(service.tasks(query, vulnId, vulnTitle, taskStatus, priority, handlerId));
    }

    @GetMapping("/export")
    @RequiresPermission("vuln:repair:list") // 导出权限复用 list（scaffold 约定）
    public ResponseEntity<String> exportTasks(
            @RequestParam(required = false) Long vulnId,
            @RequestParam(required = false) String vulnTitle,
            @RequestParam(required = false) String taskStatus,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long handlerId
    ) {
        return csv("repair-tasks.csv", service.exportTasks(vulnId, vulnTitle, taskStatus, priority, handlerId));
    }

    @GetMapping("/{id}")
    @RequiresPermission("vuln:repair:list") // 详情复用 list 权限
    public R<VulnRepairTaskRow> task(@PathVariable Long id) {
        return R.success(service.task(id));
    }

    @PostMapping
    @RequiresPermission("vuln:repair:add")
    @OperLog(module = "修复工单", action = "新增")
    public R<Long> createTask(@RequestBody TaskWriteRequest request) {
        return R.success(service.createTask(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("vuln:repair:edit")
    @OperLog(module = "修复工单", action = "修改")
    public R<Void> updateTask(@PathVariable Long id, @RequestBody TaskWriteRequest request) {
        service.updateTask(id, request);
        return R.success(null);
    }

    /** 接单：0待处理 → 1处理中。 */
    @PostMapping("/{id}/accept")
    @RequiresPermission("vuln:repair:edit")
    @OperLog(module = "修复工单", action = "接单")
    public R<Void> acceptTask(@PathVariable Long id) {
        service.acceptTask(id);
        return R.success(null);
    }

    /** 提交修复：1处理中 → 2已修复，清空上轮验证意见。 */
    @PostMapping("/{id}/fix")
    @RequiresPermission("vuln:repair:edit")
    @OperLog(module = "修复工单", action = "提交修复")
    public R<Void> fixTask(@PathVariable Long id, @RequestBody TaskActionRequest request) {
        service.fixTask(id, request);
        return R.success(null);
    }

    /** 复测：2已修复 → 3已验证（通过）| 1处理中（打回），验证意见必填。 */
    @PostMapping("/{id}/verify")
    @RequiresPermission("vuln:repair:edit")
    @OperLog(module = "修复工单", action = "复测")
    public R<Void> verifyTask(@PathVariable Long id, @RequestBody TaskActionRequest request) {
        service.verifyTask(id, request);
        return R.success(null);
    }

    /** 关闭：3已验证 → 4已关闭。 */
    @PostMapping("/{id}/close")
    @RequiresPermission("vuln:repair:edit")
    @OperLog(module = "修复工单", action = "关闭")
    public R<Void> closeTask(@PathVariable Long id) {
        service.closeTask(id);
        return R.success(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("vuln:repair:remove")
    @OperLog(module = "修复工单", action = "删除")
    public R<Void> deleteTask(@PathVariable Long id) {
        service.deleteTask(id);
        return R.success(null);
    }

    private ResponseEntity<String> csv(String filename, String content) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content);
    }
}
