package com.crm.customer.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.audit.AuditLog;
import com.crm.common.api.Result;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.common.security.CurrentUser;
import com.crm.customer.entity.CrmCustomer;
import com.crm.customer.entity.CrmFollowup;
import com.crm.customer.mapper.CrmCustomerMapper;
import com.crm.customer.mapper.CrmFollowupMapper;
import com.crm.customer.service.CustomerService;
import com.crm.lead.entity.CrmLead;
import com.crm.lead.mapper.CrmLeadMapper;
import com.crm.opportunity.entity.CrmOpportunity;
import com.crm.opportunity.mapper.CrmOpportunityMapper;
import com.crm.system.service.ScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 跟进记录接口（CRM-F1/F2）：统一承载客户/线索/商机三类对象的跟进
 */
@Tag(name = "跟进记录 CRM-F")
@RestController
@RequestMapping("/api/followups")
@RequiredArgsConstructor
public class FollowupController {

    private final CrmFollowupMapper followupMapper;
    private final CustomerService customerService;
    private final ScopeService scopeService;
    private final CrmCustomerMapper customerMapper;
    private final CrmLeadMapper leadMapper;
    private final CrmOpportunityMapper oppMapper;

    @Operation(summary = "按对象查询跟进记录")
    @GetMapping
    public Result<List<CrmFollowup>> list(@CurrentUser Long uid,
                                          @RequestParam String relType,
                                          @RequestParam Long relId) {
        if ("CUSTOMER".equals(relType)) {
            customerService.requireVisible(uid, relId);
        }
        return Result.ok(followupMapper.selectList(new LambdaQueryWrapper<CrmFollowup>()
                .eq(CrmFollowup::getRelType, relType)
                .eq(CrmFollowup::getRelId, relId)
                .orderByDesc(CrmFollowup::getCreatedAt)
                .last("LIMIT 50")));
    }

    @Operation(summary = "新增跟进记录（TODO 类型进入工作台待办）", description = "需求 CRM-F1/F2")
    @PostMapping
    public Result<Long> create(@CurrentUser Long uid, @Valid @RequestBody FollowupRequest req) {
        if ("CUSTOMER".equals(req.getRelType())) {
            customerService.requireVisible(uid, req.getRelId());
        }
        CrmFollowup f = new CrmFollowup();
        f.setRelType(req.getRelType());
        f.setRelId(req.getRelId());
        f.setContent(req.getContent());
        f.setMethod(req.getMethod() == null ? "PHONE" : req.getMethod());
        f.setNextFollowupAt(req.getNextFollowupAt());
        f.setStatus(req.getStatus() == null ? "DONE" : req.getStatus());
        f.setOwnerId(uid);
        followupMapper.insert(f);
        return Result.ok(f.getId());
    }

    // ---------------- 批次3：待办看板（FUP-DV-02，CRM-F2） ----------------

    @Operation(summary = "我的待办列表（超期置顶）", description = "需求 CRM-F2：超期任务置顶标红")
    @GetMapping("/todo")
    public Result<List<Map<String, Object>>> todo(@CurrentUser Long uid) {
        List<Long> visibleIds = scopeService.visibleOwnerIds(uid);
        LambdaQueryWrapper<CrmFollowup> wrapper = new LambdaQueryWrapper<CrmFollowup>()
                .eq(CrmFollowup::getStatus, "TODO")
                .orderByAsc(CrmFollowup::getNextFollowupAt)
                .last("LIMIT 50");
        if (visibleIds != null) {
            if (visibleIds.isEmpty()) {
                wrapper.eq(CrmFollowup::getOwnerId, -1);
            } else {
                wrapper.in(CrmFollowup::getOwnerId, visibleIds);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        List<CrmFollowup> rows = followupMapper.selectList(wrapper);

        // 对象名称回填（客户/线索/商机）
        Map<Long, String> customerNames = namesFor(rows, "CUSTOMER", id -> {
            CrmCustomer c = customerMapper.selectById(id);
            return c == null ? "-" : c.getName();
        });
        Map<Long, String> leadNames = namesFor(rows, "LEAD", id -> {
            CrmLead l = leadMapper.selectById(id);
            return l == null ? "-" : l.getCompanyName();
        });
        Map<Long, String> oppNames = namesFor(rows, "OPPORTUNITY", id -> {
            CrmOpportunity o = oppMapper.selectById(id);
            return o == null ? "-" : o.getName();
        });

        return Result.ok(rows.stream().map(f -> {
            String relName = switch (f.getRelType()) {
                case "CUSTOMER" -> customerNames.getOrDefault(f.getRelId(), "-");
                case "LEAD" -> leadNames.getOrDefault(f.getRelId(), "-");
                default -> oppNames.getOrDefault(f.getRelId(), "-");
            };
            boolean overdue = f.getNextFollowupAt() != null && f.getNextFollowupAt().isBefore(now);
            return Map.<String, Object>of(
                    "id", f.getId(),
                    "relType", f.getRelType(),
                    "relId", f.getRelId(),
                    "relName", relName,
                    "content", f.getContent(),
                    "method", f.getMethod(),
                    "nextFollowupAt", f.getNextFollowupAt() == null ? "" : f.getNextFollowupAt().toString(),
                    "overdue", overdue);
        }).toList());
    }

    @Operation(summary = "完成待办", description = "需求 CRM-F2")
    @PostMapping("/{id}/done")
    @AuditLog(action = "followup:done", targetType = "FOLLOWUP", targetId = "#id")
    public Result<Void> done(@CurrentUser Long uid, @PathVariable Long id) {
        CrmFollowup f = requireVisibleTodo(uid, id);
        f.setStatus("DONE");
        followupMapper.updateById(f);
        return Result.ok();
    }

    @Operation(summary = "待办改期", description = "需求 CRM-F2")
    @PostMapping("/{id}/reschedule")
    @AuditLog(action = "followup:reschedule", targetType = "FOLLOWUP", targetId = "#id")
    public Result<Void> reschedule(@CurrentUser Long uid, @PathVariable Long id,
                                   @Valid @RequestBody RescheduleRequest req) {
        CrmFollowup f = requireVisibleTodo(uid, id);
        f.setNextFollowupAt(req.getNextFollowupAt());
        followupMapper.updateById(f);
        return Result.ok();
    }

    private CrmFollowup requireVisibleTodo(Long uid, Long id) {
        CrmFollowup f = followupMapper.selectById(id);
        if (f == null) {
            throw new BizException(ResultCode.NOT_FOUND, "待办不存在");
        }
        if (!scopeService.canSee(uid, f.getOwnerId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (!"TODO".equals(f.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "该跟进不是待办状态");
        }
        return f;
    }

    private Map<Long, String> namesFor(List<CrmFollowup> rows, String relType,
                                       java.util.function.LongFunction<String> resolver) {
        List<Long> ids = rows.stream()
                .filter(f -> relType.equals(f.getRelType()))
                .map(CrmFollowup::getRelId).distinct().toList();
        return ids.isEmpty() ? Map.of()
                : ids.stream().collect(Collectors.toMap(id -> id, id -> resolver.apply(id), (a, b) -> a));
    }

    /** 改期请求体 */
    @Data
    static class RescheduleRequest {
        @NotNull(message = "下次跟进时间不能为空")
        private LocalDateTime nextFollowupAt;
    }

    /** 跟进请求体 */
    @Data
    static class FollowupRequest {
        @NotBlank(message = "关联对象类型不能为空")
        @Pattern(regexp = "CUSTOMER|LEAD|OPPORTUNITY", message = "关联对象类型不合法")
        private String relType;

        @NotNull(message = "关联对象ID不能为空")
        private Long relId;

        @NotBlank(message = "跟进内容不能为空")
        @Size(max = 1000, message = "跟进内容最长 1000 字")
        private String content;

        @Pattern(regexp = "PHONE|VISIT|WECHAT|EMAIL|OTHER", message = "跟进方式不合法")
        private String method;

        /** DONE / TODO；TODO 需填 nextFollowupAt */
        @Pattern(regexp = "DONE|TODO", message = "跟进状态不合法")
        private String status;

        private java.time.LocalDateTime nextFollowupAt;
    }
}
