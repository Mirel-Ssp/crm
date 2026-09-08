package com.crm.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.api.ResultCode;
import com.crm.common.api.PageResult;
import com.crm.common.exception.BizException;
import com.crm.customer.dto.CustomerQuery;
import com.crm.customer.dto.CustomerSaveRequest;
import com.crm.customer.entity.CrmContact;
import com.crm.customer.entity.CrmCustomer;
import com.crm.customer.entity.CrmCustomerTrace;
import com.crm.customer.mapper.CrmContactMapper;
import com.crm.customer.mapper.CrmCustomerMapper;
import com.crm.customer.mapper.CrmCustomerTraceMapper;
import com.crm.customer.entity.CrmFollowup;
import com.crm.customer.mapper.CrmFollowupMapper;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客户服务（CRM-C1~C6）
 * 批次3：生命周期流转留痕（C4）、查重与合并（C5）、JSONB 自定义字段（C1）、
 *        360° 时间轴（C6，聚合跟进/生命周期/商机阶段）
 * 数据范围：非 ALL 角色仅可见数据范围内客户（ScopeService）
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final List<String> LIFECYCLE_VALUES =
            List.of("POTENTIAL", "FOLLOWING", "WON", "LOST", "DORMANT");

    private final CrmCustomerMapper customerMapper;
    private final CrmContactMapper contactMapper;
    private final CrmFollowupMapper followupMapper;
    private final CrmCustomerTraceMapper traceMapper;
    private final CrmOpportunityMapper oppMapper;
    private final CrmOpportunityTraceMapper oppTraceMapper;
    private final SysUserMapper sysUserMapper;
    private final ScopeService scopeService;

    /** 分页列表：关键词/等级/状态筛选 + 数据范围过滤 + 负责人姓名批量回填 */
    public PageResult<Map<String, Object>> page(Long uid, CustomerQuery query) {
        int pageSize = Math.min(query.getPageSize() == null ? 20 : query.getPageSize(), 200);
        Page<CrmCustomer> page = new Page<>(
                query.getPageNum() == null ? 1 : query.getPageNum(), pageSize);

        // 数据范围：ALL=null 不过滤；SELF=[本人]；TEAM=本组织含下级成员
        List<Long> visibleIds = scopeService.visibleOwnerIds(uid);
        LambdaQueryWrapper<CrmCustomer> wrapper = new LambdaQueryWrapper<CrmCustomer>()
                .like(StringUtils.hasText(query.getKeyword()), CrmCustomer::getName, query.getKeyword())
                .eq(StringUtils.hasText(query.getLevel()), CrmCustomer::getLevel, query.getLevel())
                .eq(StringUtils.hasText(query.getStatus()), CrmCustomer::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getLifecycle()), CrmCustomer::getLifecycleStatus, query.getLifecycle())
                .orderByDesc(CrmCustomer::getCreatedAt);
        if (visibleIds != null) {
            // 防护：空集合生成非法 IN ()，强制空结果
            if (visibleIds.isEmpty()) {
                wrapper.eq(CrmCustomer::getOwnerId, -1);
            } else {
                wrapper.in(CrmCustomer::getOwnerId, visibleIds);
            }
        }
        Page<CrmCustomer> result = customerMapper.selectPage(page, wrapper);

        // 负责人姓名批量回填（避免 N+1）
        List<Long> ownerIds = result.getRecords().stream()
                .map(CrmCustomer::getOwnerId).distinct().toList();
        Map<Long, String> ownerNames = ownerIds.isEmpty() ? Map.of()
                : sysUserMapper.selectByIds(ownerIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));

        List<Map<String, Object>> rows = result.getRecords().stream()
                .map(c -> Map.<String, Object>of(
                        "id", c.getId(),
                        "name", c.getName(),
                        "level", nvl(c.getLevel()),
                        "industry", nvl(c.getIndustry()),
                        "region", nvl(c.getRegion()),
                        "status", nvl(c.getStatus()),
                        "lifecycleStatus", nvl(c.getLifecycleStatus()),
                        "ownerId", c.getOwnerId(),
                        "ownerName", ownerNames.getOrDefault(c.getOwnerId(), "-"),
                        "createdAt", c.getCreatedAt() == null ? "" : c.getCreatedAt().toString()))
                .toList();
        return new PageResult<>(rows, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    /** 新增：归属人=创建人；允许同名存在（V17）；软查重 checkDuplicate 已在前端作为疑似重复提示；生命周期初始 POTENTIAL 并留痕 */
    @Transactional
    public Long create(Long uid, CustomerSaveRequest req) {
        CrmCustomer c = new CrmCustomer();
        copy(req, c);
        c.setOwnerId(uid);
        if (!StringUtils.hasText(c.getStatus())) {
            c.setStatus("ACTIVE");
        }
        if (!StringUtils.hasText(c.getLevel())) {
            c.setLevel("NORMAL");
        }
        c.setLifecycleStatus("POTENTIAL");
        customerMapper.insert(c);
        writeTrace(c.getId(), "lifecycle_status", null, "POTENTIAL", uid);
        return c.getId();
    }

    /** 编辑：范围校验（非本人且非 ALL 拒绝）；允许改名到同名（V17）；等级变更留痕（CRM-C3） */
    @Transactional
    public void update(Long uid, Long id, CustomerSaveRequest req) {
        CrmCustomer c = requireVisible(uid, id);
        String oldLevel = c.getLevel();
        copy(req, c);
        customerMapper.updateById(c);
        if (StringUtils.hasText(req.getLevel()) && !req.getLevel().equals(oldLevel)) {
            writeTrace(id, "level", oldLevel, req.getLevel(), uid);
        }
    }

    /** 删除（软删除，回收站可恢复——对应需求 CRM-C3） */
    public void delete(Long uid, Long id) {
        requireVisible(uid, id);
        customerMapper.deleteById(id);
    }

    /** 详情（360° 视图 CRM-C6）：客户 + 联系人 + 最近跟进 + 全部商机 */
    public Map<String, Object> detail(Long uid, Long id) {
        CrmCustomer c = requireVisible(uid, id);
        SysUser owner = sysUserMapper.selectById(c.getOwnerId());
        List<CrmContact> contacts = contactMapper.selectList(new LambdaQueryWrapper<CrmContact>()
                .eq(CrmContact::getCustomerId, id)
                .orderByDesc(CrmContact::getIsPrimary)
                .orderByAsc(CrmContact::getId));
        List<CrmFollowup> followups = followupMapper.selectList(new LambdaQueryWrapper<CrmFollowup>()
                .eq(CrmFollowup::getRelType, "CUSTOMER")
                .eq(CrmFollowup::getRelId, id)
                .orderByDesc(CrmFollowup::getCreatedAt)
                .last("LIMIT 20"));
        List<CrmOpportunity> opportunities = oppMapper.selectList(new LambdaQueryWrapper<CrmOpportunity>()
                .eq(CrmOpportunity::getCustomerId, id)
                .orderByDesc(CrmOpportunity::getCreatedAt));
        return Map.of(
                "customer", c,
                "ownerName", owner == null ? "-" : owner.getRealName(),
                "contacts", contacts,
                "followups", followups,
                "opportunities", opportunities);
    }

    // ---------------- 批次3：生命周期 / 查重合并 / 时间轴 ----------------

    /** 生命周期流转（CRM-C4）：合法值校验 + 留痕；商机成交联动亦走此方法 */
    @Transactional
    public void ensureLifecycle(Long uid, Long customerId, String to) {
        CrmCustomer c = customerMapper.selectById(customerId);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND, "客户不存在");
        }
        String from = c.getLifecycleStatus();
        if (to.equals(from)) {
            return;
        }
        c.setLifecycleStatus(to);
        customerMapper.updateById(c);
        writeTrace(customerId, "lifecycle_status", from, to, uid);
    }

    /** 生命周期手动流转入口（CRM-C4）：操作者须可见该客户 */
    public void changeLifecycle(Long uid, Long id, String to, String reason) {
        requireVisible(uid, id);
        if (!LIFECYCLE_VALUES.contains(to)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "生命周期状态不合法");
        }
        ensureLifecycle(uid, id, to);
    }

    /** 查重（CRM-C5 / V17 增强）：名称精确重复 + 联系人电话命中；仅数据范围内客户；返回含 region/address 辅助区分 */
    public List<Map<String, Object>> checkDuplicate(Long uid, String name, String phone) {
        List<Map<String, Object>> hits = new ArrayList<>();
        List<Long> visibleIds = scopeService.visibleOwnerIds(uid);
        if (StringUtils.hasText(name)) {
            LambdaQueryWrapper<CrmCustomer> w = new LambdaQueryWrapper<CrmCustomer>()
                    .eq(CrmCustomer::getName, name);
            applyScope(w, visibleIds);
            customerMapper.selectList(w).forEach(c -> hits.add(Map.of(
                    "id", c.getId(), "name", c.getName(),
                    "region", nvl(c.getRegion()), "address", nvl(c.getAddress()),
                    "matchType", "NAME")));
        }
        if (StringUtils.hasText(phone)) {
            List<CrmContact> contacts = contactMapper.selectList(new LambdaQueryWrapper<CrmContact>()
                    .eq(CrmContact::getPhone, phone));
            List<Long> customerIds = contacts.stream().map(CrmContact::getCustomerId).distinct().toList();
            if (!customerIds.isEmpty()) {
                LambdaQueryWrapper<CrmCustomer> w = new LambdaQueryWrapper<CrmCustomer>()
                        .in(CrmCustomer::getId, customerIds);
                applyScope(w, visibleIds);
                customerMapper.selectList(w).forEach(c -> hits.add(Map.of(
                        "id", c.getId(), "name", c.getName(),
                        "region", nvl(c.getRegion()), "address", nvl(c.getAddress()),
                        "matchType", "PHONE")));
            }
        }
        return hits;
    }

    /** 合并（CRM-C5）：source 并入 target——联系人/跟进/商机整体搬迁，源客户软删；事务保证不丢数据 */
    @Transactional
    public void merge(Long uid, Long targetId, Long sourceId) {
        if (targetId.equals(sourceId)) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "不能与自身合并");
        }
        CrmCustomer target = requireVisible(uid, targetId);
        CrmCustomer source = requireVisible(uid, sourceId);

        contactMapper.update(null, new LambdaUpdateWrapper<CrmContact>()
                .eq(CrmContact::getCustomerId, sourceId)
                .set(CrmContact::getCustomerId, targetId));
        followupMapper.update(null, new LambdaUpdateWrapper<CrmFollowup>()
                .eq(CrmFollowup::getRelType, "CUSTOMER")
                .eq(CrmFollowup::getRelId, sourceId)
                .set(CrmFollowup::getRelId, targetId));
        oppMapper.update(null, new LambdaUpdateWrapper<CrmOpportunity>()
                .eq(CrmOpportunity::getCustomerId, sourceId)
                .set(CrmOpportunity::getCustomerId, targetId));

        writeTrace(targetId, "merge", String.valueOf(sourceId), target.getName(), uid);
        writeTrace(sourceId, "merge", source.getName(), "并入客户 " + targetId, uid);
        customerMapper.deleteById(sourceId);
    }

    /** 360° 时间轴（CRM-C6）：跟进 + 生命周期/等级留痕 + 商机阶段流转，按时间倒序 */
    public List<Map<String, Object>> timeline(Long uid, Long customerId) {
        requireVisible(uid, customerId);
        List<Map<String, Object>> events = new ArrayList<>();

        followupMapper.selectList(new LambdaQueryWrapper<CrmFollowup>()
                        .eq(CrmFollowup::getRelType, "CUSTOMER")
                        .eq(CrmFollowup::getRelId, customerId)
                        .orderByDesc(CrmFollowup::getCreatedAt)
                        .last("LIMIT 50"))
                .forEach(f -> events.add(Map.of(
                        "type", "FOLLOWUP",
                        "title", "跟进【" + f.getMethod() + "】",
                        "content", f.getContent(),
                        "time", f.getCreatedAt() == null ? "" : f.getCreatedAt().toString())));

        traceMapper.selectList(new LambdaQueryWrapper<CrmCustomerTrace>()
                        .eq(CrmCustomerTrace::getCustomerId, customerId)
                        .orderByDesc(CrmCustomerTrace::getCreatedAt)
                        .last("LIMIT 50"))
                .forEach(t -> events.add(Map.of(
                        "type", "TRACE",
                        "title", t.getField() + " 变更",
                        "content", (t.getFromValue() == null ? "—" : t.getFromValue()) + " → " + t.getToValue(),
                        "time", t.getCreatedAt() == null ? "" : t.getCreatedAt().toString())));

        List<CrmOpportunity> opps = oppMapper.selectList(new LambdaQueryWrapper<CrmOpportunity>()
                .eq(CrmOpportunity::getCustomerId, customerId));
        if (!opps.isEmpty()) {
            Map<Long, String> oppNames = opps.stream()
                    .collect(Collectors.toMap(CrmOpportunity::getId, CrmOpportunity::getName, (a, b) -> a));
            oppTraceMapper.selectList(new LambdaQueryWrapper<CrmOpportunityTrace>()
                            .in(CrmOpportunityTrace::getOppId, oppNames.keySet())
                            .orderByDesc(CrmOpportunityTrace::getCreatedAt)
                            .last("LIMIT 50"))
                    .forEach(t -> {
                        CrmOpportunity o = oppMapper.selectById(t.getOppId());
                        events.add(Map.of(
                                "type", "OPP_STAGE",
                                "title", "商机【" + oppNames.getOrDefault(t.getOppId(), "-") + "】阶段",
                                "content", o == null ? "" : (t.getFromStage() == null ? "建档" : "阶段 " + t.getFromStage())
                                        + " → " + t.getToStage() + (o.getStatus().equals("OPEN") ? "" : "（" + o.getStatus() + "）"),
                                "time", t.getCreatedAt() == null ? "" : t.getCreatedAt().toString()));
                    });
        }

        events.sort(Comparator.comparing((Map<String, Object> e) -> (String) e.get("time")).reversed());
        return events.size() > 100 ? events.subList(0, 100) : events;
    }

    private void writeTrace(Long customerId, String field, String from, String to, Long uid) {
        CrmCustomerTrace t = new CrmCustomerTrace();
        t.setCustomerId(customerId);
        t.setField(field);
        t.setFromValue(from);
        t.setToValue(to);
        t.setOperatorId(uid);
        traceMapper.insert(t);
    }

    /** 数据范围条件拼装（查重场景复用） */
    private void applyScope(LambdaQueryWrapper<CrmCustomer> wrapper, List<Long> visibleIds) {
        if (visibleIds != null) {
            if (visibleIds.isEmpty()) {
                wrapper.eq(CrmCustomer::getOwnerId, -1);
            } else {
                wrapper.in(CrmCustomer::getOwnerId, visibleIds);
            }
        }
    }

    /** 越权防护：仅数据范围内客户可操作（SELF=本人 / TEAM=本组织含下级 / ALL 全部） */
    public CrmCustomer requireVisible(Long uid, Long id) {
        CrmCustomer c = customerMapper.selectById(id);
        if (c == null) {
            throw new BizException(ResultCode.NOT_FOUND, "客户不存在");
        }
        if (!scopeService.canSee(uid, c.getOwnerId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return c;
    }

    private void copy(CustomerSaveRequest req, CrmCustomer c) {
        c.setName(req.getName());
        c.setLevel(req.getLevel());
        c.setIndustry(req.getIndustry());
        c.setSource(req.getSource());
        c.setRegion(req.getRegion());
        c.setAddress(req.getAddress());
        c.setStatus(req.getStatus());
        c.setRemark(req.getRemark());
        c.setCustomFields(req.getCustomFields());
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
