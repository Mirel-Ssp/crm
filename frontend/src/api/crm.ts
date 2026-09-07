import request from './request'

/** 客户行数据（对应后端 CustomerService.page 返回结构） */
export interface CustomerRow {
  id: number
  name: string
  level: string
  industry: string
  region: string
  status: string
  /** 生命周期（CRM-C4）：POTENTIAL/FOLLOWING/WON/LOST/DORMANT */
  lifecycleStatus: string
  ownerId: number
  ownerName: string
  createdAt: string
}

export interface PageData<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

export interface CustomerSaveReq {
  name: string
  level?: string
  industry?: string
  source?: string
  region?: string
  address?: string
  status?: string
  remark?: string
}

export interface Contact {
  id: number
  customerId: number
  name: string
  position?: string
  phone?: string
  email?: string
  wechat?: string
  isPrimary: number
  remark?: string
}

export interface Followup {
  id: number
  relType: string
  relId: number
  content: string
  method: string
  nextFollowupAt?: string
  status: string
  ownerId: number
  createdAt: string
}

export interface CustomerDetail {
  customer: CustomerSaveReq & {
    id: number
    ownerId: number
    createdAt: string
    lifecycleStatus?: string
    customFields?: Record<string, unknown>
  }
  ownerName: string
  contacts: Contact[]
  followups: Followup[]
  /** 批次3：360° 视图附带的商机列表 */
  opportunities?: OppRaw[]
}

/** 商机原始实体（详情/时间轴场景） */
export interface OppRaw {
  id: number
  name: string
  stage: number
  amount: number
  currency: string
  status: string
  expectedDate: string
  createdAt: string
}

export function pageCustomers(params: {
  keyword?: string
  level?: string
  status?: string
  pageNum?: number
  pageSize?: number
}): Promise<PageData<CustomerRow>> {
  return request.get('/customers', { params })
}

export function createCustomer(data: CustomerSaveReq): Promise<number> {
  return request.post('/customers', data)
}

export function updateCustomer(id: number, data: CustomerSaveReq): Promise<void> {
  return request.put(`/customers/${id}`, data)
}

export function deleteCustomer(id: number): Promise<void> {
  return request.delete(`/customers/${id}`)
}

export function customerDetail(id: number): Promise<CustomerDetail> {
  return request.get(`/customers/${id}`)
}

export function listContacts(customerId: number): Promise<Contact[]> {
  return request.get(`/customers/${customerId}/contacts`)
}

export interface ContactSaveReq {
  name: string
  position?: string
  phone?: string
  email?: string
  wechat?: string
  /** 表单开关为 boolean；后端以 1/0 存储 */
  isPrimary?: boolean
  remark?: string
}

export function createContact(customerId: number, data: ContactSaveReq): Promise<number> {
  return request.post(`/customers/${customerId}/contacts`, data)
}

export function deleteContact(customerId: number, contactId: number): Promise<void> {
  return request.delete(`/customers/${customerId}/contacts/${contactId}`)
}

export function listFollowups(relType: string, relId: number): Promise<Followup[]> {
  return request.get('/followups', { params: { relType, relId } })
}

export function createFollowup(data: {
  relType: string
  relId: number
  content: string
  method?: string
  status?: string
  nextFollowupAt?: string
}): Promise<number> {
  return request.post('/followups', data)
}

export function workbenchSummary(): Promise<{
  myCustomers: number
  todayTodo: number
  allTodo: number
  weekNewCustomers: number
}> {
  return request.get('/workbench/summary')
}

// ==================== 批次3：待办看板（CRM-F2） ====================

export interface TodoRow {
  id: number
  relType: 'CUSTOMER' | 'LEAD' | 'OPPORTUNITY'
  relId: number
  relName: string
  content: string
  method: string
  nextFollowupAt: string
  overdue: boolean
}

/** 我的待办列表（超期置顶） */
export function todoList(): Promise<TodoRow[]> {
  return request.get('/followups/todo')
}

export function todoDone(id: number): Promise<void> {
  return request.post(`/followups/${id}/done`)
}

export function todoReschedule(id: number, nextFollowupAt: string): Promise<void> {
  return request.post(`/followups/${id}/reschedule`, { nextFollowupAt })
}

// ==================== 批次3：商机（CRM-O） ====================

export interface OppRow {
  id: number
  name: string
  customerId: number
  customerName: string
  stage: number
  amount: number
  currency: string
  expectedDate: string
  ownerId: number
  ownerName: string
  status: 'OPEN' | 'WON' | 'LOST'
  loseReason: string
  createdAt: string
}

export interface OppSaveReq {
  customerId: number
  name: string
  stage?: number
  amount?: number
  currency?: string
  expectedDate?: string
  remark?: string
}

export interface OppQuery {
  keyword?: string
  stage?: number
  status?: string
  customerId?: number
  pageNum?: number
  pageSize?: number
}

export interface FunnelRow {
  stage: number
  count: number
  amount: number
  winRate: number
  weightedAmount: number
}

export interface LossRow {
  reason: string
  count: number
  amount: number
}

export function pageOpps(params: OppQuery): Promise<PageData<OppRow>> {
  return request.get('/opps', { params })
}

export function oppFunnel(from?: string, to?: string): Promise<FunnelRow[]> {
  return request.get('/opps/funnel', { params: { from, to } })
}

export function oppLossStats(from?: string, to?: string): Promise<LossRow[]> {
  return request.get('/opps/loss-stats', { params: { from, to } })
}

export function createOpp(data: OppSaveReq): Promise<number> {
  return request.post('/opps', data)
}

export function updateOpp(id: number, data: OppSaveReq): Promise<void> {
  return request.put(`/opps/${id}`, data)
}

export function deleteOpp(id: number): Promise<void> {
  return request.delete(`/opps/${id}`)
}

export function stageOpp(id: number, toStage: number, reason?: string): Promise<void> {
  return request.post(`/opps/${id}/stage`, { toStage, reason })
}

export function winOpp(id: number, reason?: string): Promise<void> {
  return request.post(`/opps/${id}/win`, reason ? { reason } : {})
}

export function loseOpp(id: number, reason: string): Promise<void> {
  return request.post(`/opps/${id}/lose`, { reason })
}

// ==================== 批次3：客户增强（CRM-C4/C5/C6） ====================

export interface DupHit {
  id: number
  name: string
  matchType: 'NAME' | 'PHONE'
}

export function checkCustomerDuplicate(name?: string, phone?: string): Promise<DupHit[]> {
  return request.get('/customers/check-duplicate', { params: { name, phone } })
}

export function mergeCustomer(targetId: number, sourceId: number): Promise<void> {
  return request.post(`/customers/${targetId}/merge`, { sourceId })
}

export function changeLifecycle(id: number, to: string, reason?: string): Promise<void> {
  return request.put(`/customers/${id}/lifecycle`, { to, reason })
}

export interface TimelineEvent {
  type: 'FOLLOWUP' | 'TRACE' | 'OPP_STAGE'
  title: string
  content: string
  time: string
}

export function customerTimeline(id: number): Promise<TimelineEvent[]> {
  return request.get(`/customers/${id}/timeline`)
}

// ==================== 批次3：报表（RPT） ====================

export interface DistRow {
  code: string
  name: string
  count: number
}

export function reportCustomer(): Promise<{
  total: number
  byLevel: DistRow[]
  byLifecycle: DistRow[]
}> {
  return request.get('/reports/customer')
}

export function reportLead(): Promise<{
  total: number
  byStatus: DistRow[]
  conversionRate: number
}> {
  return request.get('/reports/lead')
}

export function reportOpportunity(): Promise<{ funnel: FunnelRow[]; lossStats: LossRow[] }> {
  return request.get('/reports/opportunity')
}

// ==================== 跟进及时率监控（CRM-F3） ====================

export interface OverdueCustomerRow {
  id: number
  name: string
  ownerId: number
  ownerName: string
  level: string
  lifecycleStatus: string
  lastFollowupAt: string | null
  followupCount: number
  overdueDays: number
}

export interface OverdueTodoRow {
  id: number
  relType: string
  relId: number
  content: string
  method: string
  nextFollowupAt: string
  ownerId: number
  ownerName: string
  overdueDays: number
}

export interface FollowupTimelinessData {
  thresholdDays: number
  totalActive: number
  overdueCount: number
  timelyRate: number
  overdueTodoCount: number
  overdueList: OverdueCustomerRow[]
  overdueTodos: OverdueTodoRow[]
}

export function reportFollowupTimeliness(days?: number): Promise<FollowupTimelinessData> {
  return request.get('/reports/followup-timeliness', { params: days ? { days } : {} })
}

/** CSV 导出（blob 直通，不走统一 code 拦截） */
export function exportReport(type: 'customer' | 'lead' | 'opportunity' | 'followup-timeliness'): Promise<Blob> {
  return request.get(`/reports/${type}/export`, { responseType: 'blob' })
}
