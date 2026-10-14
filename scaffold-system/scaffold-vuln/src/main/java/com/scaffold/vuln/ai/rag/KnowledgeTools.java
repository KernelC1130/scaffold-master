package com.scaffold.vuln.ai.rag;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 知识库检索工具（供大模型 Function Calling 调用）
 */
@Component
public class KnowledgeTools {

    private final KnowledgePipeline knowledgePipeline;

    public KnowledgeTools(KnowledgePipeline knowledgePipeline) {
        this.knowledgePipeline = knowledgePipeline;
    }

    /** 知识库语义检索：返回最相关的若干段落（含来源文档名） */
    @Tool(description = "搜索平台知识库（漏洞修复指南、安全处置知识）。凡涉及漏洞修复方案、处置步骤、安全加固知识类的问题，先调用此工具查询再回答")
    public String searchKnowledge(
            @ToolParam(description = "检索问题或关键词，如 'SQL注入 如何修复'") String query) {
        return knowledgePipeline.search(query, 4);
    }
}
