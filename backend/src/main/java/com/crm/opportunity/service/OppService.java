package com.crm.opportunity.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.api.PageResult;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.customer.entity.CrmCustomer;
import com.crm.customer.mapper.CrmCustomerMapper;
import com.crm.customer.service.CustomerService;
import com.crm.opportunity.dto.OppQuery;
import com.crm.opportunity.dto.OppSaveRequest;
import com.crm.opportunity.entity.CrmOpportunity;
import com.crm.opportunity.entity.CrmOpportunityTrace;
import com.crm.opportunity.mapper.CrmOpportunityMapper;
import com.crm.opportunity.mapper.CrmOpportunityTraceMapper;
import com.crm.system.entity.SysUser;
import com.crm.system.mapper.SysUserMapper;
import com.crm.system.service.ScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商机服务（CRM-O1~O5）
 * 状态机：OPEN(1~5) 可自由推进/回退并留痕；WON/LOST 为终态不可再流转
 * 数据范围：owner_id 按 ScopeService 过滤；建档/流转校验客户可见性
 */
@Service
@RequiredArgsConstructor
public class OppService {

    /** 阶段默认赢率%（CRM-O5 加权预测 = Σ 金额×赢率），与字典 opportunity_stage 对应 */
    public static final Map<Integer, Integer> DEFAULT_WIN_RATES =
            Map.of(1, 10, 2, 30, 3, 50, 4, 70, 5, 90, 6, 100, 7, 0);

    private final CrmOpportunityMapper oppMapper;
    private final CrmOpportunityTraceMapper traceMapper;
    private final CustomerService customerService;
    private final CrmCustomerMapper customerMapper;
    private final SysUserMapper sysUserMapper;
    private final ScopeService scopeService;

    /** 状态机纯函数（供单测）：仅 OPEN 可流转至 1~5；终态禁止；成交/丢单走专用接口 */
    public static boolean canTransit(String status, int toStage) {
        if (!"OPEN".equals(status)) {
            return false;
        }
        return toStage >= 1 && toStage <= 5;
    }

    /** 分页列表（漏斗下钻明细）：keyword/stage/status/customerId + 数据范围 + 名称回填 */
    public PageResult<Map<String, Object>> page(Long uid, OppQuery query) {
        int pageSize = Math.min(query.getPageSize() == null ? 20 : query.getPageSize(), 200);
        Page<CrmOpportunity> page = new Page<>(
                query.getPageNum() == null ? 1 : query.getPageNum(), pageSize);

        LambdaQueryWrapper<CrmOpportunity> wrapper = new LambdaQueryWrapper<CrmOpportunity>()
                .like(StringUtils.hasText(query.getKeyword()), CrmOpportunity::getName, query.getKeyword())
                .eq(query.getCustomerId() != null, CrmOpportunity::getCustomerId, query.getCustomerId())
                .eq(query.getStage() != null, CrmOpportunity::getStage, query.getStage())
                .eq(StringUtils.hasText(query.getStatus()), CrmOpportunity::getStatus, query.getStatus())
                .orderByDesc(CrmOpportunity::getCreatedAt);
        applyScope(wrapper, uid);

        Page<CrmOpportunity> result = oppMapper.selectPage(page, wrapper);
        List<CrmOpportunity> rows = result.getRecords();

        Map<Long, String> customerNames = resolveNames(rows.stream()
                .map(CrmOpportunity::getCustomerId).distinct().toList(), id -> {
            CrmCustomer c = customerMapper.selectById(id);
            return c == null ? "-" : c.getName();
        });
        Map<Long, String> ownerNames = resolveNames(rows.stream()
                .map(CrmOpportunity::getOwnerId).distinct().toList(), id -> {
            SysUser u = sysUserMapper.selectById(id);
            return u == null ? "-" : u.getRealName();
        });

        List<Map<String, Object>> list = new ArrayList<>();
        for (CrmOpportunity o : rows) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", o.getId());
            row.put("name", o.getName());
            row.put("customerId", o.getCustomerId());
            row.put("customerName", customerNames.getOrDefault(o.getCustomerId(), "-"));
            row.put("stage", o.getStage());
            row.put("amount", o.getAmount());
            row.put("currency", o.getCurrency() == null ? "CNY" : o.getCurrency());
            row.put("expectedDate", o.getExpectedDate() == null ? "" : o.getExpectedDate().toString());
            row.put("ownerId", o.getOwnerId());
            row.put("ownerName", ownerNames.getOrDefault(o.getOwnerId(), "-"));
            row.put("status", o.getStatus());
            row.put("loseReason", o.getLoseReason() == null ? "" : o.getLoseReason());
            row.put("createdAt", o.getCreatedAt() == null ? "" : o.getCreatedAt().toString());
            list.add(row);
        }
        return new PageResult<>(list, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    /** 建档（CRM-O1）：客户须可见；默认阶段 1；客户生命周期联动「跟进中」 */
    @Transactional
    public Long create(Long uid, OppSaveRequest req) {
        customerService.requireVisible(uid, req.getCustomerId());
        CrmOpportunity o = new CrmOpportunity();
        o.setCustomerId(req.getCustomerId());
        o.setName(req.getName());
        o.setStage(req.getStage() == null ? 1 : req.getStage());
        o.setAmount(req.getAmount());
        o.setCurrency(StringUtils.hasText(req.getCurrency()) ? req.getCurrency() : "CNY");
        o.setExpectedDate(req.getExpectedDate());
        o.setStatus("OPEN");
        o.setRemark(req.getRemark());
        o.setOwnerId(uid);
        oppMapper.insert(o);
        traceMapper.insert(trace(o.getId(), null, o.getStage(), uid, "建档"));
        customerService.ensureLifecycle(uid, req.getCustomerId(), "FOLLOWING");
        return o.getId();
    }

    /** 编辑（仅 OPEN 可编辑；CRM-O1 必填校验在 DTO） */
    @Transactional
    public void update(Long uid, Long id, OppSaveRequest req) {
        CrmOpportunity o = requireVisible(uid, id);
        if (!"OPEN".equals(o.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "终态商机不可编辑");
        }
        o.setName(req.getName());
        o.setAmount(req.getAmount());
        o.setCurrency(StringUtils.hasText(req.getCurrency()) ? req.getCurrency() : o.getCurrency());
        o.setExpectedDate(req.getExpectedDate());
        o.setRemark(req.getRemark());
        oppMapper.updateById(o);
    }

    /** 阶段流转（CRM-O2：推进/回退均留痕；条件更新防并发终态竞争） */
    @Transactional
    public void stage(Long uid, Long id, int toStage, String reason) {
        if (toStage < 1 || toStage > 5) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "目标阶段仅支持 1~5");
        }
        CrmOpportunity o = requireVisible(uid, id);
        if (!canTransit(o.getStatus(), toStage)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "当前状态不允许阶段流转");
        }
        if (o.getStage().equals(toStage)) {
            return;
        }
        int from = o.getStage();
        o.setStage(toStage);
        int updated = oppMapper.update(o, new LambdaQueryWrapper<CrmOpportunity>()
                .eq(CrmOpportunity::getId, id)
                .eq(CrmOpportunity::getStatus, "OPEN"));
        if (updated == 0) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "商机状态已变化，请刷新后重试");
        }
        traceMapper.insert(trace(id, from, toStage, uid, reason));
    }

    /** 成交（CRM-O4）：stage=6 + WON；客户生命周期联动 WON */
    @Transactional
    public void win(Long uid, Long id, String winReason) {
        CrmOpportunity o = requireVisible(uid, id);
        if (!"OPEN".equals(o.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "商机已处于终态");
        }
        int from = o.getStage();
        o.setStatus("WON");
        o.setStage(6);
        if (StringUtils.hasText(winReason)) {
            o.setWinReason(winReason);
        }
        int updated = oppMapper.update(o, new LambdaQueryWrapper<CrmOpportunity>()
                .eq(CrmOpportunity::getId, id)
                .eq(CrmOpportunity::getStatus, "OPEN"));
        if (updated == 0) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "商机状态已变化，请刷新后重试");
        }
        traceMapper.insert(trace(id, from, 6, uid, winReason));
        customerService.ensureLifecycle(uid, o.getCustomerId(), "WON");
    }

    /** 丢单（CRM-O4）：原因必填（字典 lose_reason 编码）；stage=7 + LOST */
    @Transactional
    public void lose(Long uid, Long id, String loseReason) {
        if (!StringUtils.hasText(loseReason)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "丢单原因不能为空");
        }
        CrmOpportunity o = requireVisible(uid, id);
        if (!"OPEN".equals(o.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "商机已处于终态");
        }
        int from = o.getStage();
        o.setStatus("LOST");
        o.setStage(7);
        o.setLoseReason(loseReason);
        int updated = oppMapper.update(o, new LambdaQueryWrapper<CrmOpportunity>()
                .eq(CrmOpportunity::getId, id)
                .eq(CrmOpportunity::getStatus, "OPEN"));
        if (updated == 0) {
            throw new BizException(ResultCode.CONFLICT.getCode(), "商机状态已变化，请刷新后重试");
        }
        traceMapper.insert(trace(id, from, 7, uid, loseReason));
    }

    /** 删除：仅 OPEN 可删；终态保留以支撑漏斗/丢单统计 */
    @Transactional
    public void delete(Long uid, Long id) {
        CrmOpportunity o = requireVisible(uid, id);
        if (!"OPEN".equals(o.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "终态商机不可删除（保留统计口径）");
        }
        oppMapper.deleteById(id);
    }

    /** 漏斗（CRM-O3/O5）：7 阶段数量/金额/赢率/加权金额；时间筛选按创建时间 */
    public List<Map<String, Object>> funnel(Long uid, String from, String to) {
        Map<Integer, List<CrmOpportunity>> byStage = listForStats(uid, from, to).stream()
                .collect(Collectors.groupingBy(CrmOpportunity::getStage));
        List<Map<String, Object>> result = new ArrayList<>();
        for (int stage = 1; stage <= 7; stage++) {
            List<CrmOpportunity> list = byStage.getOrDefault(stage, List.of());
            long count = list.size();
            double amount = list.stream().mapToDouble(o -> o.getAmount().doubleValue()).sum();
            int rate = DEFAULT_WIN_RATES.getOrDefault(stage, 0);
            result.add(Map.of(
                    "stage", stage,
                    "count", count,
                    "amount", Math.round(amount * 100.0) / 100.0,
                    "winRate", rate,
                    "weightedAmount", Math.round(amount * rate / 100.0 * 100.0) / 100.0));
        }
        return result;
    }

    /** 丢单原因统计（CRM-O4，供 CSV 导出） */
    public List<Map<String, Object>> lossStats(Long uid, String from, String to) {
        Map<String, List<CrmOpportunity>> byReason = listForStats(uid, from, to).stream()
                .filter(o -> "LOST".equals(o.getStatus()))
                .collect(Collectors.groupingBy(o -> o.getLoseReason() == null ? "OTHER" : o.getLoseReason()));
        return byReason.entrySet().stream().map(e -> {
            double amount = e.getValue().stream().mapToDouble(o -> o.getAmount().doubleValue()).sum();
            return Map.<String, Object>of(
                    "reason", e.getKey(),
                    "count", e.getValue().size(),
                    "amount", Math.round(amount * 100.0) / 100.0);
        }).sorted((a, b) -> Long.compare(((Number) b.get("count")).longValue(),
                ((Number) a.get("count")).longValue())).toList();
    }

    /** 客户 360° 视图（CRM-C6）：该客户下全部商机 */
    public List<CrmOpportunity> listByCustomer(Long uid, Long customerId) {
        customerService.requireVisible(uid, customerId);
        LambdaQueryWrapper<CrmOpportunity> wrapper = new LambdaQueryWrapper<CrmOpportunity>()
                .eq(CrmOpportunity::getCustomerId, customerId)
                .orderByDesc(CrmOpportunity::getCreatedAt);
        applyScope(wrapper, uid);
        return oppMapper.selectList(wrapper);
    }

    /** 越权防护 */
    public CrmOpportunity requireVisible(Long uid, Long id) {
        CrmOpportunity o = oppMapper.selectById(id);
        if (o == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商机不存在");
        }
        if (!scopeService.canSee(uid, o.getOwnerId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return o;
    }

    private List<CrmOpportunity> listForStats(Long uid, String from, String to) {
        LambdaQueryWrapper<CrmOpportunity> wrapper = new LambdaQueryWrapper<CrmOpportunity>();
        if (StringUtils.hasText(from)) {
            wrapper.ge(CrmOpportunity::getCreatedAt, LocalDate.parse(from).atStartOfDay());
        }
        if (StringUtils.hasText(to)) {
            wrapper.lt(CrmOpportunity::getCreatedAt, LocalDate.parse(to).plusDays(1).atStartOfDay());
        }
        applyScope(wrapper, uid);
        return oppMapper.selectList(wrapper);
    }

    /** 数据范围过滤：ALL(null) 不加条件；空集合兜底 eq(-1) */
    private void applyScope(LambdaQueryWrapper<CrmOpportunity> wrapper, Long uid) {
        List<Long> visibleIds = scopeService.visibleOwnerIds(uid);
        if (visibleIds != null) {
            if (visibleIds.isEmpty()) {
                wrapper.eq(CrmOpportunity::getOwnerId, -1);
            } else {
                wrapper.in(CrmOpportunity::getOwnerId, visibleIds);
            }
        }
    }

    private CrmOpportunityTrace trace(Long oppId, Integer fromStage, int toStage, Long uid, String reason) {
        CrmOpportunityTrace t = new CrmOpportunityTrace();
        t.setOppId(oppId);
        t.setFromStage(fromStage);
        t.setToStage(toStage);
        t.setOperatorId(uid);
        t.setReason(reason);
        return t;
    }

    private Map<Long, String> resolveNames(List<Long> ids, java.util.function.LongFunction<String> resolver) {
        return ids.isEmpty() ? Map.of()
                : ids.stream().distinct()
                        .collect(Collectors.toMap(id -> id, id -> resolver.apply(id), (a, b) -> a));
    }
}
