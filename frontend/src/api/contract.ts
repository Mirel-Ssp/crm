import request from './request'

/**
 * 合同模块 API（CRM-R3，批次7）
 */

export interface PageData<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

export const CONTRACT_STATUS: Record<string, { label: string; tag: string }> = {
  UNSIGNED: { label: '未签署', tag: 'info' },
  SIGNED: { label: '已签署', tag: 'primary' },
  EXECUTING: { label: '履行中', tag: 'success' },
  EXPIRED: { label: '已到期', tag: 'warning' },
  TERMINATED: { label: '已终止', tag: 'danger' },
}

export interface ContractRow {
  id: number
  contractNo: string
  title: string
  customerId: number
  customerName: string
  opportunityId: number | null
  orderId: number | null
  quoteId: number | null
  ownerId: number
  ownerName: string
  amount: number
  signStatus: string
  startDate: string
  endDate: string
  reminderDays: number
  attachmentUrl: string | null
  signedAt: string
  terminatedAt: string
  terminateReason: string | null
  remark: string | null
  createdAt: string
  daysLeft?: number
}

export interface ContractSaveReq {
  title: string
  customerId: number
  opportunityId?: number
  orderId?: number
  quoteId?: number
  amount: number
  startDate?: string
  endDate?: string
  reminderDays?: number
  attachmentUrl?: string
  remark?: string
}

export function pageContracts(params: {
  keyword?: string
  signStatus?: string
  customerId?: number
  expiring?: boolean
  pageNum?: number
  pageSize?: number
}): Promise<PageData<ContractRow>> {
  return request.get('/contracts', { params })
}

export function contractDetail(id: number): Promise<ContractRow> {
  return request.get(`/contracts/${id}`)
}

export function expiringContracts(): Promise<ContractRow[]> {
  return request.get('/contracts/expiring')
}

export function createContract(data: ContractSaveReq): Promise<number> {
  return request.post('/contracts', data)
}

export function updateContract(id: number, data: ContractSaveReq): Promise<void> {
  return request.put(`/contracts/${id}`, data)
}

export function signContract(id: number): Promise<void> {
  return request.post(`/contracts/${id}/sign`)
}

export function executeContract(id: number): Promise<void> {
  return request.post(`/contracts/${id}/execute`)
}

export function terminateContract(id: number, reason?: string): Promise<void> {
  return request.post(`/contracts/${id}/terminate`, { reason })
}
