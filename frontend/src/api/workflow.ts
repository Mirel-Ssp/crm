import request from './request'

/**
 * 工作流模块 API（CRM-F4，批次7）：Flowable BPMN 任务中心
 * 报价单审批任务的业务回写走 /quotes/{id}/approve|reject（内部完成 BPMN 任务）
 */

export interface WfTaskRow {
  id: string
  name: string
  taskDefinitionKey: string
  assignee: string | null
  processDefinitionId: string
  processKey: string
  processName: string
  businessKey: string
  createdAt: string
  vars: Record<string, unknown>
}

export interface WfDefinitionRow {
  id: string
  key: string
  name: string
  version: number
  description: string | null
  deploymentId: string
}

export function wfMyTasks(): Promise<WfTaskRow[]> {
  return request.get('/workflow/tasks')
}

export function wfDefinitions(): Promise<WfDefinitionRow[]> {
  return request.get('/workflow/definitions')
}

export function wfDefinitionXml(definitionId: string): Promise<string> {
  return request.get(`/workflow/definitions/${definitionId}/xml`, { responseType: 'text' })
}

export function wfClaimTask(taskId: string): Promise<void> {
  return request.post(`/workflow/tasks/${taskId}/claim`)
}

export function wfCompleteTask(taskId: string, vars?: Record<string, unknown>): Promise<void> {
  return request.post(`/workflow/tasks/${taskId}/complete`, vars ?? {})
}
