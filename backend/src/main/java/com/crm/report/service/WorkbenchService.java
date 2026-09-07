package com.crm.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.crm.customer.entity.CrmCustomer;
import com.crm.customer.entity.CrmFollowup;
import com.crm.customer.mapper.CrmCustomerMapper;
import com.crm.customer.mapper.CrmFollowupMapper;
import com.crm.system.service.ScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作台 KPI（WSP-1）：我的客户 / 今日待跟进 / 全部待办 / 本周新增客户
 * SYS-DV-02 起按数据范围口径统计：SELF=本人；TEAM=本组织含下级；ADMIN/ALL=全量
 */
@Service
@RequiredArgsConstructor
public class WorkbenchService {

    private final CrmCustomerMapper customerMapper;
    private final CrmFollowupMapper followupMapper;
    private final ScopeService scopeService;

    public Map<String, Object> summary(Long uid) {
        List<Long> visibleIds = scopeService.visibleOwnerIds(uid);

        Long myCustomers = customerMapper.selectCount(
                ownerScope(new LambdaQueryWrapper<CrmCustomer>(), visibleIds, CrmCustomer::getOwnerId));

        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        Long todayTodo = followupMapper.selectCount(ownerScope(new LambdaQueryWrapper<CrmFollowup>()
                .eq(CrmFollowup::getStatus, "TODO")
                .ge(CrmFollowup::getNextFollowupAt, dayStart)
                .lt(CrmFollowup::getNextFollowupAt, dayEnd), visibleIds, CrmFollowup::getOwnerId));

        Long allTodo = followupMapper.selectCount(ownerScope(new LambdaQueryWrapper<CrmFollowup>()
                .eq(CrmFollowup::getStatus, "TODO"), visibleIds, CrmFollowup::getOwnerId));

        Long weekNewCustomers = customerMapper.selectCount(ownerScope(new LambdaQueryWrapper<CrmCustomer>()
                .ge(CrmCustomer::getCreatedAt, dayStart.minusDays(7)), visibleIds, CrmCustomer::getOwnerId));

        return Map.of(
                "myCustomers", myCustomers,
                "todayTodo", todayTodo,
                "allTodo", allTodo,
                "weekNewCustomers", weekNewCustomers);
    }

    /** 统一拼装 ownerId 范围条件：ALL(null) 不加；空集合兜底 eq(-1) */
    private <T, R> LambdaQueryWrapper<T> ownerScope(LambdaQueryWrapper<T> wrapper, List<Long> visibleIds,
                                                    SFunction<T, R> ownerColumn) {
        if (visibleIds != null) {
            if (visibleIds.isEmpty()) {
                wrapper.eq(ownerColumn, -1);
            } else {
                wrapper.in(ownerColumn, visibleIds);
            }
        }
        return wrapper;
    }
}
