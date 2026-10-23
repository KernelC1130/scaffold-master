package com.scaffold.system.notice.service;

import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.system.notice.domain.SysNotice;
import com.scaffold.system.notice.domain.dto.NoticeSendRequest;
import com.scaffold.system.notice.mapper.SysNoticeMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 站内通知服务：公告 + 点对点消息共用一套读写口径。
 * 定时任务、业务告警等调用 send() 即可，无需关心已读表。
 */
@Service
public class NoticeService {

    private final SysNoticeMapper noticeMapper;

    public NoticeService(SysNoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    /**
     * 发送通知。recipientId 为空 = 全员公告；有值 = 仅该用户可见。
     * 带 dedupeKey 时先查重，避免定时任务反复提醒同一条业务。
     */
    public Long send(NoticeSendRequest request, String operator) {
        String title = trim(request.getTitle());
        String content = trim(request.getContent());
        if (title.isEmpty()) throw new BusinessException("通知标题不能为空");
        if (content.isEmpty()) throw new BusinessException("通知内容不能为空");
        if (title.length() > 120) throw new BusinessException("通知标题不能超过 120 字");
        if (content.length() > 2000) throw new BusinessException("通知内容不能超过 2000 字");
        String dedupeKey = trim(request.getDedupeKey());

        // 接收人：recipientIds 优先；为空再看单个 recipientId；都为空 = 全员公告
        List<Long> recipients = new ArrayList<>();
        if (request.getRecipientIds() != null && !request.getRecipientIds().isEmpty()) {
            for (Long id : request.getRecipientIds()) {
                if (id != null && !recipients.contains(id)) recipients.add(id);
            }
        } else if (request.getRecipientId() != null) {
            recipients.add(request.getRecipientId());
        }

        Long firstId = null;
        if (recipients.isEmpty()) {
            if (!dedupeKey.isEmpty() && noticeMapper.countByDedupeKey(dedupeKey) > 0) return null;
            firstId = insert(title, content, request, null, dedupeKey, operator);
        } else {
            for (Long recipientId : recipients) {
                // 多人时按人展开，去重键带上接收人，避免一个人收到后其他人被挡掉
                String key = dedupeKey.isEmpty() ? "" : dedupeKey + ":u" + recipientId;
                if (!key.isEmpty() && noticeMapper.countByDedupeKey(key) > 0) continue;
                Long id = insert(title, content, request, recipientId, key, operator);
                if (firstId == null) firstId = id;
            }
        }
        return firstId;
    }

    private Long insert(String title, String content, NoticeSendRequest request, Long recipientId,
                        String dedupeKey, String operator) {
        SysNotice notice = new SysNotice();
        notice.setTitle(title);
        notice.setContent(content);
        notice.setCategory(normalizeCategory(request.getCategory()));
        notice.setLevel(normalizeLevel(request.getLevel()));
        notice.setRecipientId(recipientId);
        notice.setBizType(trim(request.getBizType()));
        notice.setBizId(trim(request.getBizId()));
        notice.setDedupeKey(dedupeKey.isEmpty() ? null : dedupeKey);
        notice.setCreateBy(trim(operator));
        noticeMapper.insert(notice);
        return notice.getId();
    }

    /** 简便入口：给某个用户发一条业务提醒（可带去重键）。 */
    public Long sendTo(Long recipientId, String title, String content, String category, String level,
                       String bizType, String bizId, String dedupeKey, String operator) {
        NoticeSendRequest request = new NoticeSendRequest();
        request.setRecipientId(recipientId);
        request.setTitle(title);
        request.setContent(content);
        request.setCategory(category);
        request.setLevel(level);
        request.setBizType(bizType);
        request.setBizId(bizId);
        request.setDedupeKey(dedupeKey);
        return send(request, operator);
    }

    public PageResult<SysNotice> mine(Long userId, boolean unreadOnly, int pageNum, int pageSize) {
        int page = Math.max(1, pageNum);
        int size = Math.min(100, Math.max(1, pageSize));
        List<SysNotice> rows = noticeMapper.selectMine(userId, unreadOnly, (page - 1) * size, size);
        long total = noticeMapper.countMine(userId, unreadOnly);
        return PageResult.of(rows, total, page, size);
    }

    public long unreadCount(Long userId) {
        return noticeMapper.countMine(userId, true);
    }

    public void markRead(Long id, Long userId) {
        if (noticeMapper.selectVisibleById(id, userId) == null) {
            throw new BusinessException("通知不存在或无权查看");
        }
        noticeMapper.markRead(id, userId);
    }

    public void markAllRead(Long userId) {
        noticeMapper.markAllRead(userId);
    }

    public void remove(Long id, String operator) {
        noticeMapper.softDelete(id, trim(operator));
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeCategory(String value) {
        String category = trim(value);
        return switch (category) {
            case "alert", "task", "notice" -> category;
            default -> "notice";
        };
    }

    private String normalizeLevel(String value) {
        String level = trim(value);
        return switch (level) {
            case "warn", "danger", "info" -> level;
            default -> "info";
        };
    }
}
