package com.scaffold.vuln.ai.service;

import com.scaffold.vuln.ai.domain.AiChatMessage;
import com.scaffold.vuln.ai.domain.AiChatSession;
import com.scaffold.vuln.mapper.AiChatMessageMapper;
import com.scaffold.vuln.mapper.AiChatSessionMapper;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * AI 对话会话/消息服务：会话归属校验、问答落库、历史清理。
 */
@Service
public class AiChatService {

    /** 会话标题最大长度 */
    private static final int TITLE_MAX_LEN = 20;

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;

    public AiChatService(AiChatSessionMapper sessionMapper, AiChatMessageMapper messageMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    /** 新建会话（title 取首问前 20 字，无首问时为「新对话」，首问落库时自动补全） */
    public AiChatSession createSession(Long userId, String firstQuestion) {
        AiChatSession session = new AiChatSession();
        session.setUserId(userId);
        session.setTitle(firstQuestion == null || firstQuestion.isBlank()
                ? "新对话" : truncateTitle(firstQuestion.trim()));
        Date now = new Date();
        session.setCreateTime(now);
        session.setUpdateTime(now);
        sessionMapper.insert(session);
        return session;
    }

    /** 校验会话归属（不区分「不存在」与「非本人」，避免枚举他人会话） */
    public AiChatSession getOwnedSession(Long sessionId, Long userId) {
        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new com.scaffold.common.exception.BusinessException("会话不存在");
        }
        return session;
    }

    public List<AiChatMessage> listMessages(Long sessionId) {
        return messageMapper.selectBySession(sessionId);
    }

    public List<AiChatSession> listSessions(Long userId) {
        return sessionMapper.selectByUser(userId);
    }

    /** 问答落库（user + assistant 各一条），并补全会话标题与活跃时间 */
    public void saveRound(Long sessionId, String question, String answer) {
        Date now = new Date();
        AiChatMessage userMsg = new AiChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(question);
        userMsg.setCreateTime(now);
        messageMapper.insert(userMsg);

        AiChatMessage assistantMsg = new AiChatMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(answer);
        assistantMsg.setCreateTime(new Date(now.getTime() + 1));
        messageMapper.insert(assistantMsg);

        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            if (session.getTitle() == null || session.getTitle().isBlank() || "新对话".equals(session.getTitle())) {
                session.setTitle(truncateTitle(question.trim()));
            }
            session.setUpdateTime(now);
            sessionMapper.update(session);
        }
    }

    /** 删除早于保留期的会话与消息（定时任务调用），返回删除消息数 */
    public int cleanup(int retentionDays) {
        Date time = new Date(System.currentTimeMillis() - retentionDays * 24L * 3600 * 1000);
        int messages = messageMapper.deleteBefore(time);
        sessionMapper.deleteBefore(time);
        return messages;
    }

    private String truncateTitle(String question) {
        return question.length() > TITLE_MAX_LEN ? question.substring(0, TITLE_MAX_LEN) : question;
    }
}
