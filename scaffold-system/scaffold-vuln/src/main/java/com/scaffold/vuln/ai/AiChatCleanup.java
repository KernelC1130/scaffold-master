package com.scaffold.vuln.ai;

import com.scaffold.vuln.ai.service.AiChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI 对话数据定时清理：每天凌晨 2 点删除 90 天前的会话与消息。
 */
@Component
public class AiChatCleanup {

    private static final Logger log = LoggerFactory.getLogger(AiChatCleanup.class);

    private static final int RETENTION_DAYS = 90;

    private final AiChatService aiChatService;

    public AiChatCleanup(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void clean() {
        int removed = aiChatService.cleanup(RETENTION_DAYS);
        if (removed > 0) {
            log.info("[AI] 已清理 {} 天前的对话消息 {} 条", RETENTION_DAYS, removed);
        }
    }
}
