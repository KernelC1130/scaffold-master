package com.scaffold.vuln.ai.task;

import com.scaffold.vuln.ai.service.AiChatService;
import org.springframework.stereotype.Component;

/**
 * 页面可配置定时任务的委托入口：sys_job 的 invoke_target 指向这里（白名单包），
 * 再由这里转调真正的业务方法。新增可被页面调度的任务时，在此类加公开方法即可。
 */
@Component("jobTasks")
public class JobTasks {

    private final AiChatService aiChatService;

    public JobTasks(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    /** 清理 90 天前的 AI 对话会话与消息 */
    public void aiChatClean() {
        aiChatService.cleanup(90);
    }
}
