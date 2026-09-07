package com.crm.report.controller;

import com.crm.common.api.Result;
import com.crm.common.security.CurrentUser;
import com.crm.report.service.WorkbenchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工作台 KPI（WSP-1）：我的客户 / 今日待跟进 / 全部待办 / 本周新增客户
 * SYS-DV-02 起按数据范围口径：SELF=本人；TEAM=本组织含下级；ADMIN/ALL=全量
 */
@Tag(name = "工作台 WSP")
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    private final WorkbenchService workbenchService;

    @Operation(summary = "工作台指标汇总")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@CurrentUser Long uid) {
        return Result.ok(workbenchService.summary(uid));
    }
}
