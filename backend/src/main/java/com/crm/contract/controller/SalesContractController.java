package com.crm.contract.controller;

import com.crm.common.api.PageResult;
import com.crm.common.api.Result;
import com.crm.common.audit.AuditLog;
import com.crm.common.security.CurrentUser;
import com.crm.contract.dto.ContractQuery;
import com.crm.contract.dto.ContractSaveRequest;
import com.crm.contract.service.SalesContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 合同接口（CRM-R3）
 * 权限：contract:list 查询；contract:create 创建/编辑草稿（UNSIGNED）；contract:sign 签署/履行/终止
 */
@Tag(name = "合同 CONTRACT")
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class SalesContractController {

    private final SalesContractService contractService;

    @Operation(summary = "合同分页列表", description = "需求 CRM-R3")
    @GetMapping
    @PreAuthorize("@ss.hasPerm('contract:list')")
    public Result<PageResult<Map<String, Object>>> page(@CurrentUser Long uid, ContractQuery query) {
        return Result.ok(contractService.page(uid, query));
    }

    @Operation(summary = "合同详情", description = "需求 CRM-R3")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('contract:list')")
    public Result<Map<String, Object>> detail(@CurrentUser Long uid, @PathVariable Long id) {
        return Result.ok(contractService.detail(uid, id));
    }

    @Operation(summary = "到期预警清单（30 天内到期，按到期日升序）", description = "需求 CRM-R3 到期续约预警")
    @GetMapping("/expiring")
    @PreAuthorize("@ss.hasPerm('contract:list')")
    public Result<List<Map<String, Object>>> expiring(@CurrentUser Long uid) {
        return Result.ok(contractService.expiring(uid));
    }

    @Operation(summary = "创建合同（UNSIGNED）", description = "需求 CRM-R3")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('contract:create')")
    @AuditLog(action = "contract:create", targetType = "CONTRACT")
    public Result<Long> create(@CurrentUser Long uid, @Valid @RequestBody ContractSaveRequest req) {
        return Result.ok(contractService.create(uid, req));
    }

    @Operation(summary = "编辑合同（仅 UNSIGNED）", description = "需求 CRM-R3")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('contract:create')")
    @AuditLog(action = "contract:update", targetType = "CONTRACT", targetId = "#id")
    public Result<Void> update(@CurrentUser Long uid, @PathVariable Long id,
                               @Valid @RequestBody ContractSaveRequest req) {
        contractService.update(uid, id, req);
        return Result.ok();
    }

    @Operation(summary = "签署完成（UNSIGNED→SIGNED）", description = "需求 CRM-R3")
    @PostMapping("/{id}/sign")
    @PreAuthorize("@ss.hasPerm('contract:sign')")
    @AuditLog(action = "contract:sign", targetType = "CONTRACT", targetId = "#id")
    public Result<Void> sign(@CurrentUser Long uid, @PathVariable Long id) {
        contractService.sign(uid, id);
        return Result.ok();
    }

    @Operation(summary = "开始履行（SIGNED→EXECUTING）", description = "需求 CRM-R3")
    @PostMapping("/{id}/execute")
    @PreAuthorize("@ss.hasPerm('contract:sign')")
    @AuditLog(action = "contract:execute", targetType = "CONTRACT", targetId = "#id")
    public Result<Void> execute(@CurrentUser Long uid, @PathVariable Long id) {
        contractService.execute(uid, id);
        return Result.ok();
    }

    @Operation(summary = "提前终止（→TERMINATED）", description = "需求 CRM-R3")
    @PostMapping("/{id}/terminate")
    @PreAuthorize("@ss.hasPerm('contract:sign')")
    @AuditLog(action = "contract:terminate", targetType = "CONTRACT", targetId = "#id")
    public Result<Void> terminate(@CurrentUser Long uid, @PathVariable Long id,
                                  @RequestBody(required = false) Map<String, String> body) {
        contractService.terminate(uid, id, body == null ? null : body.get("reason"));
        return Result.ok();
    }
}
