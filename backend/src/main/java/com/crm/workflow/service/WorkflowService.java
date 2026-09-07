package com.crm.workflow.service;

import com.crm.common.api.ResultCode;
import com.crm.common.exception.BizException;
import com.crm.common.security.UserState;
import com.crm.system.entity.SysUser;
import com.crm.system.mapper.SysUserMapper;
import com.crm.system.service.UserStateService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流服务（CRM-F4）：Flowable BPMN 引擎封装
 * - definitions：已部署流程定义（版本/部署时间）
 * - myTasks：本人待办（assignee=本人 或 候选组=本人角色）
 * - completeTask：按流程 key 映射业务权限（quoteApproval→quote:approve，其余→wf:manage）
 * 业务集成：SalesQuoteService.submit 启动流程，approve/reject 完成任务后回写状态
 */
@Service
@RequiredArgsConstructor
public class WorkflowService {

    /** 流程 key → 完成任务所需业务权限 */
    private static final Map<String, String> PROCESS_PERMS = Map.of(
            "quoteApproval", "quote:approve");

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final SysUserMapper sysUserMapper;
    private final UserStateService userStateService;

    // ---------------- 查询 ----------------

    /** 已部署流程定义列表（含版本与部署时间） */
    public List<Map<String, Object>> definitions() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProcessDefinition d : repositoryService.createProcessDefinitionQuery()
                .latestVersion().orderByProcessDefinitionKey().asc().list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", d.getId());
            row.put("key", d.getKey());
            row.put("name", d.getName() == null ? d.getKey() : d.getName());
            row.put("version", d.getVersion());
            row.put("description", d.getDescription());
            row.put("deploymentId", d.getDeploymentId());
            out.add(row);
        }
        return out;
    }

    /** 流程定义 BPMN XML（前端 bpmn-js 渲染） */
    public String definitionXml(String definitionId) {
        ProcessDefinition d = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(definitionId).singleResult();
        if (d == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "流程定义不存在");
        }
        try (var in = repositoryService.getResourceAsStream(d.getDeploymentId(), d.getResourceName())) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException(ResultCode.SYSTEM_ERROR.getCode(), "读取流程定义失败: " + e.getMessage());
        }
    }

    /** 本人待办：assignee=本人 或 候选组∈本人角色（未认领） */
    public List<Map<String, Object>> myTasks(Long uid) {
        SysUser user = sysUserMapper.selectById(uid);
        if (user == null) {
            return List.of();
        }
        UserState st = userStateService.load(uid);
        List<Task> tasks = new ArrayList<>(taskService.createTaskQuery()
                .taskAssignee(user.getUsername())
                .orderByTaskCreateTime().desc().list());
        List<String> roles = st == null ? List.of() : st.getRoles();
        if (!roles.isEmpty()) {
            for (Task t : taskService.createTaskQuery()
                    .taskCandidateGroupIn(roles)
                    .orderByTaskCreateTime().desc().list()) {
                if (t.getAssignee() == null || user.getUsername().equals(t.getAssignee())) {
                    tasks.add(t);
                }
            }
        }
        tasks.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));

        List<Map<String, Object>> out = new ArrayList<>();
        for (Task t : tasks) {
            out.add(toTaskRow(t, uid));
        }
        return out;
    }

    /** 完成任务（同步推进流程；权限按流程 key 映射） */
    @Transactional
    public void completeTask(Long uid, String taskId, Map<String, Object> vars) {
        Task t = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (t == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "任务不存在或已办结");
        }
        requireCompletePerm(uid, t.getProcessDefinitionId());
        Map<String, Object> completeVars = vars == null ? Map.of() : vars;
        completeVars.putIfAbsent("operator", uid);
        taskService.complete(taskId, completeVars);
    }

    /** 认领任务（候选任务转到本人名下） */
    @Transactional
    public void claimTask(Long uid, String taskId) {
        SysUser user = sysUserMapper.selectById(uid);
        Task t = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (t == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "任务不存在或已办结");
        }
        requireCompletePerm(uid, t.getProcessDefinitionId());
        taskService.claim(taskId, user.getUsername());
    }

    // ---------------- 业务集成入口（供 SalesQuoteService 调用） ----------------

    /** 启动流程实例（businessKey = 业务类型:业务ID） */
    public String startProcess(String processKey, String businessKey, Map<String, Object> vars) {
        return runtimeService.startProcessInstanceByKey(processKey, businessKey, vars).getId();
    }

    /** 按业务键查找当前活跃任务（无活跃实例返回 null，调用方按兼容模式处理） */
    public Task findActiveTask(String businessKey) {
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceBusinessKey(businessKey)
                .orderByTaskCreateTime().desc().list();
        return tasks.isEmpty() ? null : tasks.get(0);
    }

    /** 完成指定任务并携带变量（approved 等） */
    public void completeTaskById(String taskId, Map<String, Object> vars) {
        taskService.complete(taskId, vars);
    }

    /** 取消业务键下全部活跃流程实例（报价单作废等场景，避免僵尸待办） */
    public void cancelProcess(String businessKey, String reason) {
        runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey).active().list()
                .forEach(pi -> runtimeService.deleteProcessInstance(pi.getId(), reason));
    }

    // ---------------- internal ----------------

    private void requireCompletePerm(Long uid, String processDefinitionId) {
        String perm = "wf:manage";
        if (processDefinitionId != null) {
            ProcessDefinition d = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId).singleResult();
            if (d != null && PROCESS_PERMS.containsKey(d.getKey())) {
                perm = PROCESS_PERMS.get(d.getKey());
            }
        }
        UserState st = userStateService.load(uid);
        if (st == null || !st.getPermissions().contains(perm)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }

    private Map<String, Object> toTaskRow(Task t, Long uid) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", t.getId());
        row.put("name", t.getName());
        row.put("taskDefinitionKey", t.getTaskDefinitionKey());
        row.put("assignee", t.getAssignee());
        row.put("processDefinitionId", t.getProcessDefinitionId());
        ProcessDefinition d = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(t.getProcessDefinitionId()).singleResult();
        row.put("processKey", d == null ? "" : d.getKey());
        row.put("processName", d == null ? "" : d.getName());
        var instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(t.getProcessInstanceId()).singleResult();
        row.put("businessKey", instance == null ? "" : instance.getBusinessKey());
        row.put("createdAt", t.getCreateTime() == null ? "" : t.getCreateTime().toString());
        // 任务变量（如 quoteId/quoteNo/title）
        try {
            Map<String, Object> vars = taskService.getVariables(t.getId());
            Map<String, Object> safe = new LinkedHashMap<>();
            for (String k : List.of("quoteId", "quoteNo", "title", "submittedBy")) {
                if (vars.containsKey(k)) {
                    safe.put(k, vars.get(k));
                }
            }
            row.put("vars", safe);
        } catch (Exception ignore) {
            row.put("vars", Map.of());
        }
        return row;
    }
}
