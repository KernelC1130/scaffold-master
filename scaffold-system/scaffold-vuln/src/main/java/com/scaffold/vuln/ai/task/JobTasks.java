package com.scaffold.vuln.ai.task;

import com.scaffold.system.notice.service.NoticeService;
import com.scaffold.vuln.ai.service.AiChatService;
import com.scaffold.vuln.domain.VulnRepairTaskRow;
import com.scaffold.vuln.mapper.VulnRepairTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 页面可配置定时任务的委托入口：sys_job 的 invoke_target 指向这里（白名单包），
 * 再由这里转调真正的业务方法。新增可被页面调度的任务时，在此类加公开方法即可。
 */
@Component("jobTasks")
public class JobTasks {

    private static final Logger log = LoggerFactory.getLogger(JobTasks.class);

    /** 到期提醒提前量：计划完成日前 3 天内开始提醒 */
    private static final int REMIND_DAYS = 3;

    private final AiChatService aiChatService;
    private final VulnRepairTaskMapper repairTaskMapper;
    private final NoticeService noticeService;

    public JobTasks(AiChatService aiChatService, VulnRepairTaskMapper repairTaskMapper, NoticeService noticeService) {
        this.aiChatService = aiChatService;
        this.repairTaskMapper = repairTaskMapper;
        this.noticeService = noticeService;
    }

    /** 清理 90 天前的 AI 对话会话与消息 */
    public void aiChatClean() {
        aiChatService.cleanup(90);
    }

    /**
     * 扫工单：未完成工单已超期或 3 天内到期的，给负责人发站内信。
     * 同一工单同一档每天最多一条（去重键带日期与档位）。
     */
    public void scanRepairTasks() {
        LocalDate today = LocalDate.now();
        List<VulnRepairTaskRow> tasks = repairTaskMapper.selectRemindTasks(today.plusDays(REMIND_DAYS + 1));
        int sent = 0;
        for (VulnRepairTaskRow task : tasks) {
            if (task.getHandlerId() == null || task.getPlanTime() == null) continue;
            long days = ChronoUnit.DAYS.between(today, task.getPlanTime());
            boolean overdue = days < 0;
            String stage = overdue ? "overdue" : "due";
            String title = (overdue ? "工单已超期：" : "工单即将到期：") + abbreviate(task.getVulnTitle(), 40);
            Long noticeId = noticeService.sendTo(task.getHandlerId(), title, buildContent(task, days),
                    "task", overdue ? "danger" : "warn", "vuln_task", String.valueOf(task.getId()),
                    "vuln_task:" + task.getId() + ":" + stage + ":" + today, "system");
            if (noticeId != null) sent++;
        }
        log.info("工单到期扫描完成：候选 {} 条，发送 {} 条", tasks.size(), sent);
    }

    private String buildContent(VulnRepairTaskRow task, long days) {
        StringBuilder content = new StringBuilder();
        content.append("工单 #").append(task.getId())
                .append(" 的计划完成日为 ").append(task.getPlanTime()).append("，");
        if (days < 0) {
            content.append("已超期 ").append(-days).append(" 天。");
        } else if (days == 0) {
            content.append("今天到期。");
        } else {
            content.append("还有 ").append(days).append(" 天到期。");
        }
        content.append("\n漏洞：").append(blankToDash(task.getVulnTitle()));
        if (task.getAssetIp() != null && !task.getAssetIp().isBlank()) {
            content.append("\n资产：").append(task.getAssetIp());
            if (task.getAssetName() != null && !task.getAssetName().isBlank()) {
                content.append("（").append(task.getAssetName()).append("）");
            }
        }
        content.append("\n请尽快登录平台处理该工单。");
        return content.toString();
    }

    private String abbreviate(String value, int max) {
        String text = value == null ? "" : value;
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
