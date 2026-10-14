package com.scaffold.vuln.controller;

import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.vuln.ai.rag.KnowledgePipeline;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 知识库管理（重建索引 / 状态查询）。
 * 知识文件由运维放至 knowledge 目录（.md/.txt），改动后调 rebuild 重新向量化。
 */
@RestController
@RequestMapping("/api/vuln/ai/knowledge")
public class AiKnowledgeController {

    private final KnowledgePipeline knowledgePipeline;

    public AiKnowledgeController(KnowledgePipeline knowledgePipeline) {
        this.knowledgePipeline = knowledgePipeline;
    }

    /** 全量重建知识库索引（文档新增/修改后调用） */
    @PostMapping("/rebuild")
    @RequiresPermission("vuln:ai:manage")
    @OperLog(module = "AI 知识库", action = "重建索引")
    public com.scaffold.common.domain.R<Map<String, Object>> rebuild() {
        return com.scaffold.common.domain.R.success(Map.of("summary", knowledgePipeline.rebuild()));
    }

    /** 知识库状态（已索引文件数、段落数、向量模型等） */
    @GetMapping("/status")
    @RequiresPermission("vuln:ai:manage")
    public com.scaffold.common.domain.R<Map<String, Object>> status() {
        return com.scaffold.common.domain.R.success(knowledgePipeline.status());
    }
}
