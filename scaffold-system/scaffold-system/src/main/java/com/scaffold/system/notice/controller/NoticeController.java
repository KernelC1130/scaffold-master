package com.scaffold.system.notice.controller;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.system.notice.domain.SysNotice;
import com.scaffold.system.notice.domain.dto.NoticeSendRequest;
import com.scaffold.system.notice.service.NoticeService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站内通知：铃铛与通知中心。
 * 读取类接口只要求登录（每人只看得到全员公告和发给自己的消息）；
 * 发送 / 删除是管理动作，走 system:notice:add / remove 权限。
 */
@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** 我的通知（全员公告 + 发给我的），按时间倒序分页 */
    @GetMapping("/mine")
    public R<PageResult<SysNotice>> mine(PageQuery query,
                                         @RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {
        return R.success(noticeService.mine(currentUserId(),
                unreadOnly, query.normalizedPageNum(), query.normalizedPageSize()));
    }

    /** 发送记录：我发出的通知（含发给别人的） */
    @GetMapping("/sent")
    @RequiresPermission("system:notice:add")
    public R<PageResult<SysNotice>> sent(PageQuery query) {
        return R.success(noticeService.sent(currentUsername(), query.normalizedPageNum(), query.normalizedPageSize()));
    }

    /** 铃铛角标：未读数 */
    @GetMapping("/unread-count")
    public R<Map<String, Long>> unreadCount() {
        return R.success(Map.of("count", noticeService.unreadCount(currentUserId())));
    }

    @PutMapping("/{id}/read")
    public R<Void> read(@PathVariable Long id) {
        noticeService.markRead(id, currentUserId());
        return R.success(null);
    }

    @PutMapping("/read-all")
    public R<Void> readAll() {
        noticeService.markAllRead(currentUserId());
        return R.success(null);
    }

    @PostMapping
    @RequiresPermission("system:notice:add")
    @OperLog(module = "通知中心", action = "发送通知")
    public R<Long> send(@RequestBody NoticeSendRequest request) {
        return R.success(noticeService.send(request, currentUsername()));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:notice:remove")
    @OperLog(module = "通知中心", action = "删除通知")
    public R<Void> remove(@PathVariable Long id) {
        noticeService.remove(id, currentUsername());
        return R.success(null);
    }

    /** JWT 过滤器把 userId claim 放进了 details，与 AI 会话等处保持同一取法 */
    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof Long userId) {
            return userId;
        }
        throw new IllegalStateException("未登录或会话已失效");
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }
}
