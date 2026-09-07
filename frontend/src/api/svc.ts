import request from './request'

/**
 * 服务工单 API（批次5 SVC：CRM-S1 流转 / CRM-S2 回访满意度）
 * 状态机：OPEN → PROCESSING → RESOLVED → CLOSED（CLOSED 终态）
 */

/** el-tag type 属性联合类型（消除 as any，检测报告 P2 #6） */
export type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

export const TICKET_STATUS: Record<string, { label: string; tag: TagType }> = {
  OPEN: { label: '待处理', tag: 'warning' },
  PROCESSING: { label: '处理中', tag: 'primary' },
  RESOLVED: { label: '已解决', tag: 'success' },
  CLOSED: { label: '已关闭', tag: 'info' },
}

export const TICKET_PRIORITY: Record<string, { label: string; tag: TagType }> = {
  LOW: { label: '低', tag: 'info' },
  MEDIUM: { label: '中', tag: 'primary' },
  HIGH: { label: '高', tag: 'warning' },
  URGENT: { label: '紧急', tag: 'danger' },
}

export const TICKET_TYPE: Record<string, string> = {
  CONSULT: '咨询',
  COMPLAINT: '投诉',
  AFTER_SALE: '售后',
  OTHER: '其他',
}

export interface TicketRow {
  id: number
  no: string
  customerId: number
  contactId: number | null
  type: string
  priority: string
  title: string
  content: string
  assigneeId: number | null
  status: string
  slaDueAt: string
  resolvedAt: string
  satisfaction: number | null
  remark: string
  createdAt: string
}

export interface TicketDetail extends TicketRow {
  customerName: string
  contactName: string
  contactPhone: string
  assigneeName: string
}

export interface TicketSave {
  customerId: number
  contactId?: number
  type: string
  priority?: string
  title: string
  content: string
  slaDueAt?: string
  assigneeId?: number
}

export function pageTickets(params: {
  status?: string
  type?: string
  priority?: string
  keyword?: string
  pageNum?: number
  pageSize?: number
}): Promise<{ list: TicketRow[]; total: number; pages: number }> {
  return request.get('/tickets', { params })
}

export function ticketDetail(id: number): Promise<TicketDetail> {
  return request.get(`/tickets/${id}`)
}

export function createTicket(data: TicketSave): Promise<number> {
  return request.post('/tickets', data)
}

export function assignTicket(id: number, assigneeId: number): Promise<void> {
  return request.post(`/tickets/${id}/assign`, { assigneeId })
}

export function processTicket(id: number): Promise<void> {
  return request.post(`/tickets/${id}/process`, {})
}

export function resolveTicket(id: number, remark?: string): Promise<void> {
  return request.post(`/tickets/${id}/resolve`, remark ? { remark } : {})
}

export function closeTicket(id: number): Promise<void> {
  return request.post(`/tickets/${id}/close`, {})
}

export function visitTicket(id: number, data: { content: string; satisfaction: number; nextFollowupAt?: string }): Promise<void> {
  return request.post(`/tickets/${id}/visit`, data)
}

export interface SatisfactionSummary {
  ratedCount: number
  avgScore: number
  distribution: Record<string, number>
  resolvedTotal: number
}

export function satisfactionSummary(): Promise<SatisfactionSummary> {
  return request.get('/tickets/satisfaction/summary')
}

/** SLA 超期预警行（批次6：未关闭且已超期，附客户/负责人与超期分钟数） */
export interface SlaWarningRow extends TicketRow {
  customerName: string
  assigneeName: string
  overdueMinutes: number
}

export function slaWarning(): Promise<SlaWarningRow[]> {
  return request.get('/tickets/sla/warning')
}
