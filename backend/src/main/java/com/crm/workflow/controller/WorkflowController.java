package com.crm.workflow.controller;

import com.crm.common.api.Result;
import com.crm.common.security.CurrentUser;
import com.crm.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工作流接口（CRM-F4）
 * 权限：1201 wf:task:list 任务中心查看；1202 wf:manage 任务处理
 * 完成任务的权限按流程 key 二次校验（quoteApproval → quote:approve）
 */
@Tag(name = "工作流 WF")
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @Operation(summary = "已部署流程定义列表", description = "需求 CRM-F4：BPMN 流程定义（最新版本）")
    @GetMapping("/definitions")
    @PreAuthorize("@ss.hasPerm('wf:task:list')")
    public Result<List<Map<String, Object>>> definitions() {
        return Result.ok(workflowService.definitions());
    }

    @Operation(summary = "流程定义 BPMN XML（前端 bpmn-js 渲染）", description = "需求 CRM-F4：流程可视化")
    @GetMapping(value = "/definitions/{definitionId}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize("@ss.hasPerm('wf:task:list')")
    public String definitionXml(@PathVariable String definitionId) {
        return workflowService.definitionXml(definitionId);
    }

    @Operation(summary = "本人待办任务（指派 + 候选组）", description = "需求 CRM-F4：任务中心")
    @GetMapping("/tasks")
    @PreAuthorize("@ss.hasPerm('wf:task:list')")
    public Result<List<Map<String, Object>>> myTasks(@CurrentUser Long uid) {
        return Result.ok(workflowService.myTasks(uid));
    }

    @Operation(summary = "认领任务（候选 → 本人）", description = "需求 CRM-F4")
    @PostMapping("/tasks/{taskId}/claim")
    @PreAuthorize("@ss.hasPerm('wf:manage')")
    public Result<Void> claim(@CurrentUser Long uid, @PathVariable String taskId) {
        workflowService.claimTask(uid, taskId);
        return Result.ok();
    }

    @Operation(summary = "完成任务（携带 approved 等变量，同步推进流程）", description = "需求 CRM-F4")
    @PostMapping("/tasks/{taskId}/complete")
    @PreAuthorize("@ss.hasPerm('wf:manage')")
    public Result<Void> complete(@CurrentUser Long uid, @PathVariable String taskId,
                                  @RequestBody(required = false) Map<String, Object> vars) {
        workflowService.completeTask(uid, taskId, vars);
        return Result.ok();
    }
}
