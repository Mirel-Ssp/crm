package com.crm.trade.controller;

import com.crm.common.api.PageResult;
import com.crm.common.api.Result;
import com.crm.common.security.CurrentUser;
import com.crm.trade.service.NotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 动态流/通知接口（RTP-DV-01 轮询兜底服务端）
 * 订单创建/审批/驳回/取消、汇款登记/确认/驳回、核销完成全事件写入 notify_message，
 * V5 升级（RTP-DV-02/03）：落库后经 /ws/notify 实时推送在线用户，
 * 本接口保留为轮询兜底与已读管理（前端 WS 断线自动降级 5s 轮询）
 * 权限：807 trade:feed
 */
@Tag(name = "交易动态流 RTP")
@RestController
@RequestMapping("/api/trade/notify")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;

    @Operation(summary = "未读通知数（轮询徽标）", description = "需求 RTP-DV-01")
    @GetMapping("/unread-count")
    @PreAuthorize("@ss.hasPerm('trade:feed')")
    public Result<Long> unreadCount(@CurrentUser Long uid) {
        return Result.ok(notifyService.unreadCount(uid));
    }

    @Operation(summary = "我的动态流（分页，时间倒序）", description = "需求 RTP-DV-01")
    @GetMapping("/feed")
    @PreAuthorize("@ss.hasPerm('trade:feed')")
    public Result<PageResult<Map<String, Object>>> feed(@CurrentUser Long uid,
                                                        @RequestParam(required = false) Integer pageNum,
                                                        @RequestParam(required = false) Integer pageSize) {
        return Result.ok(notifyService.feed(uid, pageNum, pageSize));
    }

    @Operation(summary = "标记单条已读", description = "需求 RTP-DV-01")
    @PostMapping("/{id}/read")
    @PreAuthorize("@ss.hasPerm('trade:feed')")
    public Result<Void> markRead(@CurrentUser Long uid, @PathVariable Long id) {
        notifyService.markRead(uid, id);
        return Result.ok();
    }

    @Operation(summary = "全部已读", description = "需求 RTP-DV-01")
    @PostMapping("/read-all")
    @PreAuthorize("@ss.hasPerm('trade:feed')")
    public Result<Void> markAllRead(@CurrentUser Long uid) {
        notifyService.markAllRead(uid);
        return Result.ok();
    }
}
