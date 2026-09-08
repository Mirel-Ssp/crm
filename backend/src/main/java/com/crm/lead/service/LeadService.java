package com.crm.lead.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crm.common.api.PageResult;
import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.customer.entity.CrmCustomer;
import com.crm.customer.mapper.CrmCustomerMapper;
import com.crm.lead.dto.LeadQuery;
import com.crm.lead.dto.LeadSaveRequest;
import com.crm.lead.entity.CrmLead;
import com.crm.lead.mapper.CrmLeadMapper;
import com.crm.system.entity.SysUser;
import com.crm.system.mapper.SysUserMapper;
import com.crm.system.service.ScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 线索服务（CRM-L 最小闭环：建档/列表/领取/分配/转客户/作废）
 * - 状态机条件 UPDATE：影响行数=0 → 40000（并发冲突"线索已被他人处理"）
 * - 手机号防重：同号码存在 PENDING/CLAIMED/ASSIGNED 有效线索 → 拒绝
 * - 转客户：事务内建 crm_customer（重名校验）+ 回写 converted_customer_id/converted_at
 * - 数据范围：公共池全员可见；我的线索按 visibleOwnerIds 过滤
 */
@Service
@RequiredArgsConstructor
public class LeadService {

    private static final List<String> ACTIVE_STATUSES = List.of("PENDING", "CLAIMED", "ASSIGNED");

    private final CrmLeadMapper leadMapper;
    private final CrmCustomerMapper customerMapper;
    private final SysUserMapper userMapper;
    private final ScopeService scopeService;

    public PageResult<Map<String, Object>> page(Long uid, LeadQuery query) {
        int pageSize = Math.min(query.getPageSize() == null ? 20 : query.getPageSize(), 200);
        Page<CrmLead> page = new Page<>(
                query.getPageNum() == null ? 1 : query.getPageNum(), pageSize);

        boolean publicPool = "public".equalsIgnoreCase(query.getPool());
        LambdaQueryWrapper<CrmLead> wrapper = new LambdaQueryWrapper<CrmLead>()
                .like(StringUtils.hasText(query.getKeyword()), CrmLead::getCompanyName, query.getKeyword())
                .eq(StringUtils.hasText(query.getStatus()), CrmLead::getStatus, query.getStatus())
                .orderByDesc(CrmLead::getCreatedAt);
        if (publicPool) {
            wrapper.isNull(CrmLead::getOwnerId);
        } else {
            List<Long> visibleIds = scopeService.visibleOwnerIds(uid);
            if (visibleIds != null) {
                if (visibleIds.isEmpty()) {
                    wrapper.eq(CrmLead::getOwnerId, -1);
                } else {
                    wrapper.in(CrmLead::getOwnerId, visibleIds);
                }
            }
            wrapper.isNotNull(CrmLead::getOwnerId);
        }
        Page<CrmLead> result = leadMapper.selectPage(page, wrapper);

        // 负责人姓名批量回填
        List<Long> ownerIds = result.getRecords().stream()
                .map(CrmLead::getOwnerId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> ownerNames = ownerIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(ownerIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));

        List<Map<String, Object>> rows = result.getRecords().stream()
                .map(l -> Map.<String, Object>of(
                        "id", l.getId(),
                        "companyName", l.getCompanyName(),
                        "contactName", l.getContactName(),
                        "contactPhone", nvl(l.getContactPhone()),
                        "source", nvl(l.getSource()),
                        "ownerId", l.getOwnerId() == null ? 0 : l.getOwnerId(),
                        "ownerName", l.getOwnerId() == null ? "公共池" : ownerNames.getOrDefault(l.getOwnerId(), "-"),
                        "status", l.getStatus(),
                        "convertedCustomerId", l.getConvertedCustomerId() == null ? 0 : l.getConvertedCustomerId(),
                        "createdAt", l.getCreatedAt() == null ? "" : l.getCreatedAt().toString()))
                .toList();
        return new PageResult<>(rows, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    /** 分配目标候选：数据范围内启用成员（供分配下拉） */
    public List<Map<String, Object>> assignableUsers(Long uid) {
        List<Long> visibleIds = scopeService.visibleOwnerIds(uid);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, "ACTIVE")
                .orderByAsc(SysUser::getId);
        if (visibleIds != null) {
            if (visibleIds.isEmpty()) {
                return List.of();
            }
            wrapper.in(SysUser::getId, visibleIds);
        }
        return userMapper.selectList(wrapper).stream()
                .map(u -> Map.<String, Object>of("id", u.getId(), "realName", u.getRealName()))
                .toList();
    }

    /** 公共池建档（owner=NULL，PENDING） */
    @Transactional
    public Long create(Long uid, LeadSaveRequest req) {
        checkPhoneDuplicate(req.getContactPhone(), null);
        CrmLead lead = new CrmLead();
        copy(req, lead);
        lead.setOwnerId(null);
        lead.setStatus("PENDING");
        leadMapper.insert(lead);
        return lead.getId();
    }

    /** 编辑：CONVERTED 终态禁改 */
    @Transactional
    public void update(Long uid, Long id, LeadSaveRequest req) {
        CrmLead lead = requireVisible(uid, id);
        if ("CONVERTED".equals(lead.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "已转客户的线索不可编辑");
        }
        checkPhoneDuplicate(req.getContactPhone(), id);
        copy(req, lead);
        leadMapper.updateById(lead);
    }

    public Map<String, Object> detail(Long uid, Long id) {
        CrmLead lead = requireVisible(uid, id);
        return Map.of(
                "lead", lead,
                "ownerName", lead.getOwnerId() == null ? "公共池"
                        : nvl(userMapper.selectById(lead.getOwnerId()) == null ? "-"
                                : userMapper.selectById(lead.getOwnerId()).getRealName()));
    }

    /** 删除：CONVERTED 禁删（留痕） */
    @Transactional
    public void delete(Long uid, Long id) {
        CrmLead lead = requireVisible(uid, id);
        if ("CONVERTED".equals(lead.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "已转客户的线索不可删除");
        }
        leadMapper.deleteById(id);
    }

    /** 领取：PENDING → CLAIMED，owner=当前用户 */
    @Transactional
    public void claim(Long uid, Long id) {
        int rows = leadMapper.update(null, new LambdaUpdateWrapper<CrmLead>()
                .eq(CrmLead::getId, id)
                .eq(CrmLead::getStatus, "PENDING")
                .set(CrmLead::getStatus, "CLAIMED")
                .set(CrmLead::getOwnerId, uid));
        requireUpdated(rows);
    }

    /** 分配：PENDING → ASSIGNED，指定范围内成员 */
    @Transactional
    public void assign(Long uid, Long id, Long targetUserId) {
        CrmLead lead = requireVisible(uid, id);
        SysUser target = scopeService.requireActiveUser(targetUserId);
        // 非 ALL 范围下只能分配给数据范围内成员
        if (!scopeService.canSee(uid, target.getId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        int rows = leadMapper.update(null, new LambdaUpdateWrapper<CrmLead>()
                .eq(CrmLead::getId, id)
                .eq(CrmLead::getStatus, "PENDING")
                .set(CrmLead::getStatus, "ASSIGNED")
                .set(CrmLead::getOwnerId, target.getId()));
        requireUpdated(rows);
    }

    /**
     * 转客户：CLAIMED/ASSIGNED → CONVERTED
     * 事务内：建 crm_customer（owner=线索负责人；V17 起允许同名存在）→ 条件回写 CONVERTED
     */
    @Transactional
    public Long convert(Long uid, Long id) {
        CrmLead lead = requireVisible(uid, id);
        if (!"CLAIMED".equals(lead.getStatus()) && !"ASSIGNED".equals(lead.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "仅已领取/已分配的线索可转客户");
        }
        Long ownerId = lead.getOwnerId() == null ? uid : lead.getOwnerId();

        CrmCustomer customer = new CrmCustomer();
        customer.setName(lead.getCompanyName());
        customer.setLevel("NORMAL");
        customer.setSource(lead.getSource());
        customer.setOwnerId(ownerId);
        customer.setStatus("ACTIVE");
        customer.setRemark("由线索转入（线索ID " + lead.getId() + "）");
        customerMapper.insert(customer);

        int rows = leadMapper.update(null, new LambdaUpdateWrapper<CrmLead>()
                .eq(CrmLead::getId, id)
                .in(CrmLead::getStatus, "CLAIMED", "ASSIGNED")
                .set(CrmLead::getStatus, "CONVERTED")
                .set(CrmLead::getConvertedCustomerId, customer.getId())
                .set(CrmLead::getConvertedAt, LocalDateTime.now()));
        requireUpdated(rows);
        return customer.getId();
    }

    /** 作废：非终态 → INVALID */
    @Transactional
    public void invalidate(Long uid, Long id) {
        requireVisible(uid, id);
        int rows = leadMapper.update(null, new LambdaUpdateWrapper<CrmLead>()
                .eq(CrmLead::getId, id)
                .in(CrmLead::getStatus, "PENDING", "CLAIMED", "ASSIGNED")
                .set(CrmLead::getStatus, "INVALID"));
        requireUpdated(rows);
    }

    /** 可见性：公共池（owner=null）全员可见；否则须在数据范围内 */
    private CrmLead requireVisible(Long uid, Long id) {
        CrmLead lead = leadMapper.selectById(id);
        if (lead == null) {
            throw new BizException(ResultCode.NOT_FOUND, "线索不存在");
        }
        if (lead.getOwnerId() != null && !scopeService.canSee(uid, lead.getOwnerId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return lead;
    }

    /** 同手机号存在有效线索则拒绝 */
    private void checkPhoneDuplicate(String phone, Long excludeId) {
        Long dup = leadMapper.selectCount(new LambdaQueryWrapper<CrmLead>()
                .eq(CrmLead::getContactPhone, phone)
                .in(CrmLead::getStatus, ACTIVE_STATUSES)
                .ne(excludeId != null, CrmLead::getId, excludeId));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "该手机号已存在有效线索");
        }
    }

    /** 条件 UPDATE 影响行数=0 → 并发冲突 */
    private void requireUpdated(int rows) {
        if (rows == 0) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "线索已被他人处理，请刷新后重试");
        }
    }

    private void copy(LeadSaveRequest req, CrmLead lead) {
        lead.setCompanyName(req.getCompanyName());
        lead.setContactName(req.getContactName());
        lead.setContactPhone(req.getContactPhone());
        lead.setSource(req.getSource());
        lead.setRemark(req.getRemark());
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
