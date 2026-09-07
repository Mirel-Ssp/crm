package com.crm.customer.controller;

import com.crm.common.audit.AuditLog;
import com.crm.common.api.PageResult;
import com.crm.common.api.Result;
import com.crm.common.security.CurrentUser;
import com.crm.customer.dto.CustomerQuery;
import com.crm.customer.dto.CustomerSaveRequest;
import com.crm.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 客户接口（CRM-C1/C2/C3；需求编号见 @Operation description）
 * SYS-DV-02：权限点校验 + 数据范围过滤（CustomerService.requireVisible）
 */
@Tag(name = "客户管理 CRM-C")
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @Operation(summary = "分页列表", description = "需求 CRM-C1")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('customer:list')")
    public Result<PageResult<Map<String, Object>>> page(@CurrentUser Long uid, CustomerQuery query) {
        return Result.ok(customerService.page(uid, query));
    }

    @Operation(summary = "客户新增", description = "需求 CRM-C1")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('customer:create')")
    @AuditLog(action = "customer:create", targetType = "CUSTOMER")
    public Result<Long> create(@CurrentUser Long uid, @Valid @RequestBody CustomerSaveRequest req) {
        return Result.ok(customerService.create(uid, req));
    }

    @Operation(summary = "客户编辑", description = "需求 CRM-C2")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('customer:list')")
    @AuditLog(action = "customer:update", targetType = "CUSTOMER", targetId = "#id")
    public Result<Void> update(@CurrentUser Long uid, @PathVariable Long id,
                               @Valid @RequestBody CustomerSaveRequest req) {
        customerService.update(uid, id, req);
        return Result.ok();
    }

    @Operation(summary = "客户删除（软删除）", description = "需求 CRM-C3")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('customer:list')")
    @AuditLog(action = "customer:delete", targetType = "CUSTOMER", targetId = "#id")
    public Result<Void> delete(@CurrentUser Long uid, @PathVariable Long id) {
        customerService.delete(uid, id);
        return Result.ok();
    }

    @Operation(summary = "客户详情（360°：联系人/跟进/商机）", description = "需求 CRM-C1/C6")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('customer:list')")
    public Result<Map<String, Object>> detail(@CurrentUser Long uid, @PathVariable Long id) {
        return Result.ok(customerService.detail(uid, id));
    }

    // ---------------- 批次3：CRM-C4/C5/C6 ----------------

    @Operation(summary = "360° 时间轴（跟进+留痕+商机阶段）", description = "需求 CRM-C6")
    @GetMapping("/{id}/timeline")
    @PreAuthorize("@ss.hasPerm('customer:list')")
    public Result<List<Map<String, Object>>> timeline(@CurrentUser Long uid, @PathVariable Long id) {
        return Result.ok(customerService.timeline(uid, id));
    }

    @Operation(summary = "生命周期流转（留痕）", description = "需求 CRM-C4")
    @PutMapping("/{id}/lifecycle")
    @PreAuthorize("@ss.hasPerm('customer:list')")
    @AuditLog(action = "customer:lifecycle", targetType = "CUSTOMER", targetId = "#id")
    public Result<Void> changeLifecycle(@CurrentUser Long uid, @PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        customerService.changeLifecycle(uid, id, body.getOrDefault("to", ""), body.get("reason"));
        return Result.ok();
    }

    @Operation(summary = "查重（名称/电话）", description = "需求 CRM-C5，录入时提示疑似重复")
    @GetMapping("/check-duplicate")
    @PreAuthorize("@ss.hasPerm('customer:list')")
    public Result<List<Map<String, Object>>> checkDuplicate(@CurrentUser Long uid,
                                                            @RequestParam(required = false) String name,
                                                            @RequestParam(required = false) String phone) {
        return Result.ok(customerService.checkDuplicate(uid, name, phone));
    }

    @Operation(summary = "客户合并（source 并入 target，事务搬迁关联数据）", description = "需求 CRM-C5")
    @PostMapping("/{id}/merge")
    @PreAuthorize("@ss.hasPerm('customer:merge')")
    @AuditLog(action = "customer:merge", targetType = "CUSTOMER", targetId = "#id")
    public Result<Void> merge(@CurrentUser Long uid, @PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long sourceId = body.get("sourceId");
        if (sourceId == null) {
            return Result.fail(40000, "sourceId 不能为空");
        }
        customerService.merge(uid, id, sourceId);
        return Result.ok();
    }
}
