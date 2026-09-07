package com.crm.svc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.api.PageResult;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.customer.entity.CrmContact;
import com.crm.customer.entity.CrmCustomer;
import com.crm.customer.entity.CrmFollowup;
import com.crm.customer.mapper.CrmContactMapper;
import com.crm.customer.mapper.CrmCustomerMapper;
import com.crm.customer.mapper.CrmFollowupMapper;
import com.crm.customer.service.CustomerService;
import com.crm.svc.dto.TicketQuery;
import com.crm.svc.dto.TicketSaveRequest;
import com.crm.svc.dto.VisitRequest;
import com.crm.svc.entity.ServiceTicket;
import com.crm.svc.mapper.ServiceTicketMapper;
import com.crm.system.mapper.SysUserMapper;
import com.crm.system.service.ScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 服务工单服务（CRM-S1 流转 + CRM-S2 回访满意度）
 * 数据范围：ALL 全量；TEAM/SELF 按 assignee_id 或创建人过滤
 * 指派权限：仅数据范围 TEAM/ALL 用户可指派他人；SELF 用户仅可认领给自己
 */
@Service
@RequiredArgsConstructor
public class ServiceTicketService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Random RANDOM = new Random();
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");

    private final ServiceTicketMapper ticketMapper;
    private final CrmCustomerMapper customerMapper;
    private final CustomerService customerService;
    private final CrmContactMapper contactMapper;
    private final CrmFollowupMapper followupMapper;
    private final ScopeService scopeService;
    private final SysUserMapper sysUserMapper;
    private final com.crm.trade.service.NotifyService notifyService;

    // ---------------- CRM-S1 列表/详情 ----------------

    public PageResult<Map<String, Object>> page(Long uid, TicketQuery q) {
        Page<ServiceTicket> page = new Page<>(q.getPageNum() == null ? 1 : q.getPageNum(),
                Math.min(q.getPageSize() == null ? 10 : q.getPageSize(), 100));
        LambdaQueryWrapper<ServiceTicket> w = new LambdaQueryWrapper<ServiceTicket>()
                .eq(StringUtils.hasText(q.getStatus()), ServiceTicket::getStatus, q.getStatus())
                .eq(StringUtils.hasText(q.getType()), ServiceTicket::getType, q.getType())
                .eq(StringUtils.hasText(q.getPriority()), ServiceTicket::getPriority, q.getPriority())
                .and(StringUtils.hasText(q.getKeyword()), x -> x
                        .like(ServiceTicket::getNo, q.getKeyword())
                        .or().like(ServiceTicket::getTitle, q.getKeyword()))
                .orderByDesc(ServiceTicket::getCreatedAt);
        applyScope(uid, w);
        Page<ServiceTicket> result = ticketMapper.selectPage(page, w);
        List<Map<String, Object>> list = new ArrayList<>();
        for (ServiceTicket t : result.getRecords()) {
            list.add(toRow(t, false, uid));
        }
        return new PageResult<>(list, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    public Map<String, Object> detail(Long uid, Long id) {
        return toRow(requireVisible(uid, id), true, uid);
    }

    /**
     * SLA 超期预警（批次6 增强）：未关闭且已过 SLA 截止的工单，按超期最久排序，最多 50 条
     * 数据范围与列表一致（TEAM/SELF 按 assignee/创建人过滤）
     */
    public List<Map<String, Object>> slaWarning(Long uid) {
        LambdaQueryWrapper<ServiceTicket> w = new LambdaQueryWrapper<ServiceTicket>()
                .isNotNull(ServiceTicket::getSlaDueAt)
                .lt(ServiceTicket::getSlaDueAt, LocalDateTime.now())
                .in(ServiceTicket::getStatus, TicketStateMachine.OPEN, TicketStateMachine.PROCESSING)
                .orderByAsc(ServiceTicket::getSlaDueAt)
                .last("LIMIT 50");
        applyScope(uid, w);
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ServiceTicket t : ticketMapper.selectList(w)) {
            Map<String, Object> row = toRow(t, false, uid);
            CrmCustomer c = customerMapper.selectById(t.getCustomerId());
            row.put("customerName", c == null ? "" : c.getName());
            if (t.getAssigneeId() != null) {
                var u = sysUserMapper.selectById(t.getAssigneeId());
                row.put("assigneeName", u == null ? "" : u.getRealName());
            }
            row.put("overdueMinutes", java.time.Duration.between(t.getSlaDueAt(), now).toMinutes());
            out.add(row);
        }
        return out;
    }

    // ---------------- CRM-S1 创建/指派/流转 ----------------

    @Transactional
    public Long create(Long uid, TicketSaveRequest req) {
        CrmCustomer customer = customerService.requireVisible(uid, req.getCustomerId());
        if (req.getContactId() != null) {
            CrmContact c = contactMapper.selectById(req.getContactId());
            if (c == null || !req.getCustomerId().equals(c.getCustomerId())) {
                throw new BizException(ResultCode.BAD_REQUEST.getCode(), "联系人不属于该客户");
            }
        }
        String priority = StringUtils.hasText(req.getPriority()) ? req.getPriority() : "MEDIUM";
        if (!PRIORITIES.contains(priority)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "优先级仅支持 LOW/MEDIUM/HIGH/URGENT");
        }
        if (req.getAssigneeId() != null) {
            requireCanAssign(uid, req.getAssigneeId());
        }

        ServiceTicket t = new ServiceTicket();
        t.setNo("ST" + LocalDateTime.now().format(NO_FMT) + String.format("%03d", RANDOM.nextInt(1000)));
        t.setCustomerId(req.getCustomerId());
        t.setContactId(req.getContactId());
        t.setType(req.getType());
        t.setPriority(priority);
        t.setTitle(req.getTitle());
        t.setContent(req.getContent());
        t.setAssigneeId(req.getAssigneeId());
        t.setStatus(TicketStateMachine.OPEN);
        t.setSlaDueAt(req.getSlaDueAt());
        ticketMapper.insert(t);

        if (req.getAssigneeId() != null) {
            notifyService.publish(req.getAssigneeId(), "TICKET_EVENT", "新工单已指派",
                    "工单 " + t.getNo() + "（" + customer.getName() + "）已指派给您", "TICKET", t.getId());
        }
        return t.getId();
    }

    /** 指派/改派（CRM-S1；经理指派他人，业务员仅可认领给自己） */
    @Transactional
    public void assign(Long uid, Long id, Long assigneeId) {
        requireCanAssign(uid, assigneeId);
        ServiceTicket t = requireVisible(uid, id);
        if (TicketStateMachine.isTerminal(t.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "工单已关闭，不可指派");
        }
        t.setAssigneeId(assigneeId);
        ticketMapper.updateById(t);
        notifyService.publish(assigneeId, "TICKET_EVENT", "工单已指派",
                "工单 " + t.getNo() + " 已指派给您", "TICKET", t.getId());
    }

    /** 开始处理：OPEN → PROCESSING（处理人或经理） */
    @Transactional
    public void process(Long uid, Long id) {
        ServiceTicket t = requireVisible(uid, id);
        transit(t, TicketStateMachine.PROCESSING, uid);
        ticketMapper.updateById(t);
    }

    /** 解决：OPEN/PROCESSING → RESOLVED（记录解决时间，通知创建人） */
    @Transactional
    public void resolve(Long uid, Long id, String remark) {
        ServiceTicket t = requireVisible(uid, id);
        transit(t, TicketStateMachine.RESOLVED, uid);
        t.setResolvedAt(LocalDateTime.now());
        if (StringUtils.hasText(remark)) {
            t.setRemark(remark);
        }
        ticketMapper.updateById(t);
        if (t.getCreatedBy() != null && !t.getCreatedBy().equals(uid)) {
            notifyService.publish(t.getCreatedBy(), "TICKET_EVENT", "工单已解决",
                    "工单 " + t.getNo() + " 已解决，可安排回访", "TICKET", t.getId());
        }
    }

    /** 关闭：RESOLVED → CLOSED（终态） */
    @Transactional
    public void close(Long uid, Long id) {
        ServiceTicket t = requireVisible(uid, id);
        transit(t, TicketStateMachine.CLOSED, uid);
        ticketMapper.updateById(t);
    }

    // ---------------- CRM-S2 回访 + 满意度 ----------------

    /** 回访登记：写入统一跟进流（relType=TICKET）并记录满意度（RESOLVED/CLOSED 可回访） */
    @Transactional
    public void visit(Long uid, Long id, VisitRequest req) {
        ServiceTicket t = requireVisible(uid, id);
        if (!TicketStateMachine.canRate(t.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "工单解决后方可回访评分");
        }
        CrmFollowup f = new CrmFollowup();
        f.setRelType("TICKET");
        f.setRelId(id);
        f.setContent(req.getContent());
        f.setMethod("PHONE");
        f.setStatus("DONE");
        f.setNextFollowupAt(req.getNextFollowupAt());
        f.setOwnerId(uid);
        followupMapper.insert(f);

        t.setSatisfaction(req.getSatisfaction());
        ticketMapper.updateById(t);
    }

    /** 满意度统计（CRM-S2 验收：统计报表可用；按数据范围过滤） */
    public Map<String, Object> satisfactionSummary(Long uid) {
        List<ServiceTicket> tickets = ticketMapper.selectList(new LambdaQueryWrapper<ServiceTicket>()
                .isNotNull(ServiceTicket::getSatisfaction));
        List<ServiceTicket> scoped = tickets.stream()
                .filter(t -> inScope(uid, t)).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ratedCount", scoped.size());
        double avg = scoped.stream().mapToInt(ServiceTicket::getSatisfaction).average().orElse(0);
        data.put("avgScore", Math.round(avg * 100) / 100.0);
        Map<String, Long> dist = new LinkedHashMap<>();
        for (int s = 1; s <= 5; s++) {
            final int star = s;
            dist.put(String.valueOf(star), scoped.stream().filter(t -> t.getSatisfaction() == star).count());
        }
        data.put("distribution", dist);
        data.put("resolvedTotal", ticketMapper.selectCount(new LambdaQueryWrapper<ServiceTicket>()
                .isNotNull(ServiceTicket::getResolvedAt)));
        return data;
    }

    // ---------------- 内部方法 ----------------

    private void transit(ServiceTicket t, String to, Long uid) {
        if (!TicketStateMachine.canTransit(t.getStatus(), to)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "工单状态不允许从 " + t.getStatus() + " 变更为 " + to);
        }
        t.setStatus(to);
        t.setUpdatedBy(uid);
    }

    /** 数据范围过滤：TEAM/SELF 按 assignee 或创建人 */
    private void applyScope(Long uid, LambdaQueryWrapper<ServiceTicket> w) {
        List<Long> ids = scopeService.visibleOwnerIds(uid);
        if (ids == null) {
            return;
        }
        w.and(x -> x.in(ServiceTicket::getAssigneeId, ids).or().in(ServiceTicket::getCreatedBy, ids));
    }

    private boolean inScope(Long uid, ServiceTicket t) {
        List<Long> ids = scopeService.visibleOwnerIds(uid);
        if (ids == null) {
            return true;
        }
        return (t.getAssigneeId() != null && ids.contains(t.getAssigneeId()))
                || (t.getCreatedBy() != null && ids.contains(t.getCreatedBy()));
    }

    private ServiceTicket requireVisible(Long uid, Long id) {
        ServiceTicket t = ticketMapper.selectById(id);
        if (t == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!inScope(uid, t)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return t;
    }

    /** 指派校验：目标必须为可用用户；SELF 范围用户只能认领给自己 */
    private void requireCanAssign(Long uid, Long assigneeId) {
        if (assigneeId == null) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "指派对象不能为空");
        }
        if (assigneeId.equals(uid)) {
            return;
        }
        if (scopeService.scopeOf(uid) == ScopeService.Scope.SELF) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        if (!scopeService.canSee(uid, assigneeId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        scopeService.requireActiveUser(assigneeId);
    }

    private Map<String, Object> toRow(ServiceTicket t, boolean detail, Long uid) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", t.getId());
        row.put("no", t.getNo());
        row.put("customerId", t.getCustomerId());
        row.put("contactId", t.getContactId());
        row.put("type", t.getType());
        row.put("priority", t.getPriority());
        row.put("title", t.getTitle());
        row.put("content", t.getContent());
        row.put("assigneeId", t.getAssigneeId());
        row.put("status", t.getStatus());
        row.put("slaDueAt", t.getSlaDueAt() == null ? "" : t.getSlaDueAt().toString());
        row.put("resolvedAt", t.getResolvedAt() == null ? "" : t.getResolvedAt().toString());
        row.put("satisfaction", t.getSatisfaction());
        row.put("remark", t.getRemark());
        row.put("createdAt", t.getCreatedAt() == null ? "" : t.getCreatedAt().toString());
        if (detail) {
            CrmCustomer c = customerMapper.selectById(t.getCustomerId());
            row.put("customerName", c == null ? "" : c.getName());
            if (t.getContactId() != null) {
                CrmContact ct = contactMapper.selectById(t.getContactId());
                row.put("contactName", ct == null ? "" : ct.getName());
                row.put("contactPhone", ct == null ? "" : ct.getPhone());
            }
            if (t.getAssigneeId() != null) {
                var u = sysUserMapper.selectById(t.getAssigneeId());
                row.put("assigneeName", u == null ? "" : u.getRealName());
            }
        }
        return row;
    }
}
