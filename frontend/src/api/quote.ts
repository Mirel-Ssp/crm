import request from './request'

/**
 * 报价单模块 API（CRM-R1，批次7）
 * 金额均由服务端计算，前端仅展示
 */

export interface PageData<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

export const QUOTE_STATUS: Record<string, { label: string; tag: string }> = {
  DRAFT: { label: '草稿', tag: 'info' },
  SUBMITTED: { label: '待审批', tag: 'warning' },
  APPROVED: { label: '已通过', tag: 'success' },
  REJECTED: { label: '已驳回', tag: 'danger' },
  CONVERTED: { label: '已转订单', tag: 'primary' },
  VOID: { label: '已作废', tag: 'info' },
}

export interface QuoteRow {
  id: number
  quoteNo: string
  title: string
  opportunityId: number | null
  customerId: number
  customerName: string
  ownerId: number
  ownerName: string
  status: string
  discountRate: number
  taxRate: number
  totalAmount: number
  discountAmount: number
  taxAmount: number
  finalAmount: number
  validUntil: string
  approvedAt: string
  rejectedReason: string | null
  remark: string | null
  createdAt: string
  items?: QuoteItem[]
}

export interface QuoteItem {
  id: number
  quoteId: number
  itemId: number | null
  name: string
  spec: string | null
  quantity: number
  price: number
  amount: number
  sort: number
}

export interface QuoteSaveReq {
  title: string
  customerId: number
  opportunityId?: number
  discountRate?: number
  taxRate?: number
  validUntil?: string
  remark?: string
  items: { itemId?: number | null; name: string; spec?: string; quantity: number; price: number }[]
}

export function pageQuotes(params: {
  keyword?: string
  status?: string
  customerId?: number
  opportunityId?: number
  pageNum?: number
  pageSize?: number
}): Promise<PageData<QuoteRow>> {
  return request.get('/quotes', { params })
}

export function quoteDetail(id: number): Promise<QuoteRow> {
  return request.get(`/quotes/${id}`)
}

export function createQuote(data: QuoteSaveReq): Promise<number> {
  return request.post('/quotes', data)
}

export function updateQuote(id: number, data: QuoteSaveReq): Promise<void> {
  return request.put(`/quotes/${id}`, data)
}

export function submitQuote(id: number): Promise<void> {
  return request.post(`/quotes/${id}/submit`)
}

export function approveQuote(id: number, reason?: string): Promise<void> {
  return request.post(`/quotes/${id}/approve`, { reason })
}

export function rejectQuote(id: number, reason: string): Promise<void> {
  return request.post(`/quotes/${id}/reject`, { reason })
}

export function voidQuote(id: number, reason?: string): Promise<void> {
  return request.delete(`/quotes/${id}`, { params: { reason } })
}

export function convertQuote(id: number, direction = 'BUY'): Promise<number[]> {
  return request.post(`/quotes/${id}/convert`, null, { params: { direction } })
}
