import request from './request'

/**
 * 多维统计 API（批次5 STAT：ST-1 总量同环比 / ST-3 明细导出 / ST-4 业绩排名 / ST-5 标的分析）
 * 数据读 business_stat 聚合表（每日 02:30 预计算 + 手动重建）
 */

export const STAT_DIMS = [
  { value: 'DAY', label: '日' },
  { value: 'MONTH', label: '月' },
  { value: 'QUARTER', label: '季' },
  { value: 'YEAR', label: '年' },
]

export interface StatRow {
  statDate: string
  orderCount: number
  orderAmount: number
  customerCount: number
  remitAmount: number
  arriveRate: number
}

export interface StatSummary {
  rows: (StatRow & { prevAmount: number; prevCount: number })[]
  totalAmount: number
  totalCount: number
  amountChainRatio: number | null
  countChainRatio: number | null
}

export function statSummary(dim: string, from?: string, to?: string): Promise<StatSummary> {
  return request.get('/stat/summary', { params: { dim, from, to } })
}

export interface RankRow extends StatRow {
  rank: number
  ownerName: string
}

export function ownerRank(dim: string, from?: string, to?: string): Promise<RankRow[]> {
  return request.get('/stat/rank', { params: { dim, from, to } })
}

export interface ItemRankRow extends StatRow {
  rank: number
  itemId: number
  itemCode: string
  itemName: string
}

export function itemRank(dim: string, from?: string, to?: string): Promise<ItemRankRow[]> {
  return request.get('/stat/items', { params: { dim, from, to } })
}

export interface StatDetailRow {
  orderNo: string
  customerName: string
  itemName: string
  direction: string
  quantity: number
  price: number
  amount: number
  feeAmount: number
  totalAmount: number
  status: string
  remitStatus: string
  ownerName: string
  createdAt: string
}

export function statDetail(params: {
  customerId?: number
  itemId?: number
  status?: string
  direction?: string
  from?: string
  to?: string
  pageNum?: number
  pageSize?: number
}): Promise<{ list: StatDetailRow[]; total: number; pages: number }> {
  return request.get('/stat/detail', { params })
}

export function rebuildStat(dim?: string): Promise<number> {
  return request.post('/stat/rebuild', null, { params: { dim } })
}

/** CSV 导出（UTF-8 BOM，附件下载；携带 Authorization 头） */
export async function exportDetailCsv(params: {
  customerId?: number
  itemId?: number
  status?: string
  direction?: string
  from?: string
  to?: string
}): Promise<void> {
  const { useUserStore } = await import('@/stores/user')
  const qs = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') qs.set(k, String(v))
  }
  const res = await fetch(`/api/stat/detail/export?${qs.toString()}`, {
    headers: { Authorization: `Bearer ${useUserStore().token}` },
  })
  if (!res.ok) throw new Error('导出失败')
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `trade-detail-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}
