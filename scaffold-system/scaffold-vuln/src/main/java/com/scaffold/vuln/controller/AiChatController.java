package com.scaffold.vuln.controller;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.scaffold.common.domain.R;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.vuln.ai.AiModelFactory;
import com.scaffold.vuln.ai.domain.AiChatMessage;
import com.scaffold.vuln.ai.domain.AiChatSession;
import com.scaffold.vuln.ai.service.AiChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AI 助手 Controller（同步对话 + SSE 流式对话 + 会话管理）。
 *
 * 对话历史持久化在服务端（ai_chat_session/ai_chat_message）：
 * 前端只传 sessionId + question，最近 10 条上下文由后端从消息表读取
 */
@RestController
@RequestMapping("/api/vuln/ai")
public class AiChatController {

    /** 多轮上下文最多回放的消息条数（超出丢弃最早的） */
    private static final int MAX_HISTORY = 10;

    private final AiModelFactory aiModelFactory;
    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

    public AiChatController(AiModelFactory aiModelFactory, AiChatService aiChatService, ObjectMapper objectMapper) {
        this.aiModelFactory = aiModelFactory;
        this.aiChatService = aiChatService;
        this.objectMapper = objectMapper;
    }

    /** 新建会话（title 取首问前 20 字，无首问时为「新对话」，首问落库时自动补全） */
    @PostMapping("/chat/new")
    @RequiresPermission("vuln:ai:chat")
    public R<Map<String, Object>> newSession(@RequestBody(required = false) NewSessionBody body) {
        String firstQuestion = body == null ? null : body.question();
        AiChatSession session = aiChatService.createSession(currentUserId(), firstQuestion);
        return R.success(Map.of("sessionId", session.getSessionId(), "title", session.getTitle()));
    }

    /** 当前用户的会话列表（按最后活跃时间倒序） */
    @GetMapping("/chat/sessions")
    @RequiresPermission("vuln:ai:chat")
    public R<Map<String, Object>> sessions() {
        return R.success(Map.of("list", aiChatService.listSessions(currentUserId())));
    }

    /** 某会话的完整消息（校验归属，前端刷新后恢复对话用） */
    @GetMapping("/chat/history/{sessionId}")
    @RequiresPermission("vuln:ai:chat")
    public R<Map<String, Object>> history(@PathVariable Long sessionId) {
        aiChatService.getOwnedSession(sessionId, currentUserId());
        return R.success(Map.of("list", aiChatService.listMessages(sessionId)));
    }

    /** AI 对话（同步）：history 由后端从消息表读取（最近 10 条），不信任前端传入 */
    @PostMapping("/chat")
    @RequiresPermission("vuln:ai:chat")
    @OperLog(module = "AI 助手", action = "对话")
    public R<Map<String, Object>> chat(@RequestBody AiChatRequest req) {
        AiChatSession session = resolveSession(req);
        List<Message> history = recentHistory(session.getSessionId());
        String answer = aiModelFactory.getChatClient()
                .prompt()
                .messages(history)
                .user(req.question())
                .call()
                .content();
        aiChatService.saveRound(session.getSessionId(), req.question(), answer);
        return R.success(Map.of("answer", answer == null ? "" : answer));
    }

    /**
     * AI 对话（SSE 流式，打字机效果）：逐 chunk 推 data:{"delta":"..."}，
     * 结束推 data:[DONE]，异常推 data:{"error":"..."}；
     * X-Accel-Buffering: no 让 Nginx 对本响应关闭缓冲（打字机效果的前提）
     */
    @PostMapping("/chat/stream")
    @RequiresPermission("vuln:ai:chat")
    public ResponseEntity<StreamingResponseBody> chatStream(@RequestBody AiChatRequest req) {
        AiChatSession session = resolveSession(req);
        List<Message> history = recentHistory(session.getSessionId());
        String question = req.question();
        StreamingResponseBody body = out -> {
            StringBuilder fullAnswer = new StringBuilder();
            try {
                ChatClient client = aiModelFactory.getChatClient();
                Flux<String> flux = client.prompt()
                        .messages(history)
                        .user(question)
                        .stream()
                        .content();
                for (String delta : flux.toIterable()) {
                    String piece = delta == null ? "" : delta;
                    fullAnswer.append(piece);
                    out.write(("data: " + objectMapper.writeValueAsString(Map.of("delta", piece)) + "\n\n")
                            .getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                // 问答落库（写入 [DONE] 前，保证前端收到 [DONE] 时历史已可查）
                aiChatService.saveRound(session.getSessionId(), question, fullAnswer.toString());
                out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception e) {
                // 客户端断开（IOException）或业务异常：尝试推送 error 帧（断开时静默失败）
                String msg = e instanceof BusinessException be ? be.getMessage()
                        : "生成失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage());
                try {
                    out.write(("data: " + objectMapper.writeValueAsString(Map.of("error", msg)) + "\n\n")
                            .getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (Exception ignore) {
                }
            }
        };
        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }

    /** 校验请求并返回归属当前用户的会话（sessionId 缺失/非本人时抛异常） */
    private AiChatSession resolveSession(AiChatRequest req) {
        if (req.question() == null || req.question().isBlank()) {
            throw new BusinessException("问题不能为空");
        }
        if (req.sessionId() == null || req.sessionId() <= 0) {
            throw new BusinessException("会话不存在，请先新建会话");
        }
        return aiChatService.getOwnedSession(req.sessionId(), currentUserId());
    }

    /** 从消息表读取该会话最近 MAX_HISTORY 条消息，转成 Spring AI messages（时间正序） */
    private List<Message> recentHistory(Long sessionId) {
        List<AiChatMessage> all = aiChatService.listMessages(sessionId);
        int from = Math.max(0, all.size() - MAX_HISTORY);
        List<Message> messages = new ArrayList<>();
        for (AiChatMessage item : all.subList(from, all.size())) {
            if (item.getContent() == null || item.getContent().isBlank()) {
                continue;
            }
            if ("user".equalsIgnoreCase(item.getRole())) {
                messages.add(new UserMessage(item.getContent()));
            } else if ("assistant".equalsIgnoreCase(item.getRole())) {
                messages.add(new AssistantMessage(item.getContent()));
            }
        }
        return messages;
    }

    /** 当前登录用户 ID（JwtAuthenticationFilter 把 JWT 的 userId claim 放进了 details） */
    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof Long userId) {
            return userId;
        }
        throw new BusinessException("无法识别当前用户，请重新登录");
    }

    /** 对话请求体：sessionId 当前会话；question 当前问题 */
    public record AiChatRequest(Long sessionId, String question) {}

    /** 新建会话请求体（question 可选：作为标题来源） */
    public record NewSessionBody(String question) {}
}
