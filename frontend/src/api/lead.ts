import request from './request'
import type { PageResp } from './system'

/**
 * 线索管理 API（CRM-L 最小闭环，对接 /api/leads/**）
 */
export interface LeadRow {
  id: number
  companyName: string
  contactName: string
  contactPhone: string
  source: string
  ownerId: number
  ownerName: string
  status: string
  convertedCustomerId: number
  createdAt: string
}

export interface LeadSaveReq {
  companyName: string
  contactName: string
  contactPhone: string
  source?: string
  remark?: string
}

export interface AssignableUser {
  id: number
  realName: string
}

export function pageLeads(params: {
  pool?: string
  status?: string
  keyword?: string
  pageNum?: number
  pageSize?: number
}): Promise<PageResp<LeadRow>> {
  return request.get('/leads', { params })
}

export function createLead(data: LeadSaveReq): Promise<number> {
  return request.post('/leads', data)
}

export function updateLead(id: number, data: LeadSaveReq): Promise<void> {
  return request.put(`/leads/${id}`, data)
}

export function deleteLead(id: number): Promise<void> {
  return request.delete(`/leads/${id}`)
}

export function claimLead(id: number): Promise<void> {
  return request.post(`/leads/${id}/claim`)
}

export function assignLead(id: number, userId: number): Promise<void> {
  return request.post(`/leads/${id}/assign`, { userId })
}

export function convertLead(id: number): Promise<number> {
  return request.post(`/leads/${id}/convert`)
}

export function invalidateLead(id: number): Promise<void> {
  return request.post(`/leads/${id}/invalidate`)
}

/** 可分配成员（数据范围内启用成员） */
export function assignableUsers(): Promise<AssignableUser[]> {
  return request.get('/leads/assignable')
}
