import request from './request'
import type { TagType } from './svc'

/**
 * 客户价值评分 API（批次5 VA：VA-1 五维加权 / VA-2 分层 / VA-4 趋势 / VA-5 沉默唤醒）
 * 分层：HIGH_VALUE≥80 / POTENTIAL 60-79 / TO_ACTIVATE 40-59 / AT_RISK<40
 */

export const SCORE_TIER: Record<string, { label: string; tag: TagType }> = {
  HIGH_VALUE: { label: '高价值', tag: 'danger' },
  POTENTIAL: { label: '潜力', tag: 'primary' },
  TO_ACTIVATE: { label: '待激活', tag: 'warning' },
  AT_RISK: { label: '流失风险', tag: 'info' },
}

export interface ScoreRow {
  id: number
  customerId: number
  score: number
  tier: string
  dimFreq: number
  dimAmount: number
  dimActive: number
  dimRemittance: number
  dimFollowup: number
  manual: number
  calcDate: string
  customerName: string
  ownerId: number
}

export function pageScores(params: {
  tier?: string
  keyword?: string
  pageNum?: number
  pageSize?: number
}): Promise<{ list: ScoreRow[]; total: number; pages: number }> {
  return request.get('/value/scores', { params })
}

export function tierDistribution(): Promise<{ tier: string; count: number }[]> {
  return request.get('/value/distribution')
}

export function scoreTrend(customerId: number, days?: number): Promise<{ calcDate: string; score: number; tier: string }[]> {
  return request.get(`/value/scores/${customerId}/trend`, { params: { days } })
}

export interface SilentRow {
  customerId: number
  customerName: string
  ownerId: number | null
  ownerName: string
  lastOrderAt: string
  lastFollowupAt: string
  silentDays: number
}

export function silentList(): Promise<SilentRow[]> {
  return request.get('/value/silent')
}

export function wakeAssign(customerId: number, assigneeId: number): Promise<void> {
  return request.post(`/value/silent/${customerId}/assign`, { assigneeId })
}

export function manualAdjust(customerId: number, score: number, reason: string): Promise<void> {
  return request.post('/value/manual', { customerId, score, reason })
}

export function recalcAll(): Promise<number> {
  return request.post('/value/recalc', {})
}

export interface WeightConfig {
  WEIGHT_FREQ: number
  WEIGHT_AMOUNT: number
  WEIGHT_ACTIVE: number
  WEIGHT_REMIT: number
  WEIGHT_FOLLOWUP: number
  SILENT_DAYS: number
}

export function getWeights(): Promise<WeightConfig> {
  return request.get('/value/weights')
}

export function updateWeights(w: Partial<WeightConfig>): Promise<void> {
  return request.put('/value/weights', w)
}
