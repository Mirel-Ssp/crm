package com.crm.system.controller;

import com.crm.common.audit.AuditLog;
import com.crm.common.api.Result;
import com.crm.system.dto.DictSaveRequest;
import com.crm.system.service.SysDictService;
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
 * 数据字典接口（SYS-DV-05）
 * 读：登录即可（业务下拉共用）；写：system:dict
 */
@Tag(name = "系统管理-字典")
@RestController
@RequestMapping("/api/dicts")
@RequiredArgsConstructor
public class SystemDictController {

    private final SysDictService dictService;

    @Operation(summary = "字典项查询（type 可选）")
    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String type) {
        return Result.ok(dictService.listByType(type));
    }

    @Operation(summary = "新增字典项")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('system:dict')")
    @AuditLog(action = "dict:create", targetType = "DICT")
    public Result<Long> create(@Valid @RequestBody DictSaveRequest req) {
        return Result.ok(dictService.create(req));
    }

    @Operation(summary = "编辑字典项")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:dict')")
    @AuditLog(action = "dict:update", targetType = "DICT", targetId = "#id")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DictSaveRequest req) {
        dictService.update(id, req);
        return Result.ok(null);
    }

    @Operation(summary = "删除字典项")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPerm('system:dict')")
    @AuditLog(action = "dict:delete", targetType = "DICT", targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        dictService.delete(id);
        return Result.ok(null);
    }
}
