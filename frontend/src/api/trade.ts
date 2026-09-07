import request from './request'

/**
 * 交易模块 API（批次4：TRD 标的 / ORD 订单 / REM 汇款核销 / RTP 通知 / WSP 工作台）
 * 金额均由服务端计算，前端仅展示
 */

export interface PageData<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

// ==================== 状态字典（前端展示映射） ====================

export const ORDER_STATUS: Record<string, { label: string; tag: string }> = {
  PENDING_CONFIRM: { label: '待审批', tag: 'warning' },
  CONFIRMED: { label: '已确认', tag: 'primary' },
  PARTIAL_DEALT: { label: '部分成交', tag: 'primary' },
  FULL_DEALT: { label: '全部成交', tag: 'success' },
  PARTIAL_CANCELLED: { label: '部分取消', tag: 'info' },
  CANCELLED: { label: '已取消', tag: 'danger' },
}

export const REMIT_STATUS: Record<string, { label: string; tag: string }> = {
  PENDING_CONFIRM: { label: '待确认', tag: 'warning' },
  CONFIRMED: { label: '已到账', tag: 'primary' },
  PARTIALLY_WRITTEN_OFF: { label: '部分核销', tag: 'primary' },
  WRITTEN_OFF: { label: '已核销', tag: 'success' },
  REJECTED: { label: '已驳回', tag: 'danger' },
}

export const ITEM_STATUS: Record<string, { label: string; tag: string }> = {
  DRAFT: { label: '草稿', tag: 'info' },
  LISTED: { label: '已上架', tag: 'success' },
  DELISTED: { label: '已下架', tag: 'warning' },
}

export const fmtMoney = (n: number | null | undefined) =>
  (n ?? 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })

export const fmtTime = (t: string | null | undefined) => (t ? t.replace('T', ' ').slice(0, 19) : '-')

// ==================== TRD 标的（TB-1/2/3） ====================

export interface TradeItemRow {
  id: number
  code: string
  name: string
  category: string
  market: string
  referencePrice: number
  riskLevel: string
  status: string
  feeRate: number
  minQuantity: number
  listedAt: string
  delistedAt: string
  remark: string
  createdAt: string
}

export interface PriceLogRow {
  id: number
  itemId: number
  oldPrice: number | null
  newPrice: number
  operatorId: number
  createdAt: string
}

export interface TradeRuleRow {
  id: number
  ruleKey: string
  ruleValue: number
  remark: string
  updatedAt: string
}

export interface TradeItemSave {
  code: string
  name: string
  category: string
  market?: string
  referencePrice: number
  riskLevel?: string
  feeRate?: number
  minQuantity?: number
  remark?: string
}

export function pageTradeItems(params: { keyword?: string; category?: string; status?: string }): Promise<TradeItemRow[]> {
  return request.get('/trade/items', { params })
}

export function createTradeItem(data: TradeItemSave): Promise<number> {
  return request.post('/trade/items', data)
}

export function updateTradeItem(id: number, data: TradeItemSave): Promise<void> {
  return request.put(`/trade/items/${id}`, data)
}

export function changeItemPrice(id: number, newPrice: number): Promise<void> {
  return request.post(`/trade/items/${id}/price`, { newPrice })
}

export function itemPriceLogs(id: number): Promise<PriceLogRow[]> {
  return request.get(`/trade/items/${id}/price-logs`)
}

export function listItem(id: number): Promise<void> {
  return request.post(`/trade/items/${id}/list`)
}

export function delistItem(id: number): Promise<void> {
  return request.post(`/trade/items/${id}/delist`)
}

export function tradeRules(): Promise<TradeRuleRow[]> {
  return request.get('/trade/items/rules')
}

export function updateTradeRule(id: number, value: number): Promise<void> {
  return request.put(`/trade/items/rules/${id}`, { value })
}

// ==================== ORD 订单（OD-1~5） ====================

export interface OrderRow {
  id: number
  orderNo: string
  itemId: number
  itemName: string
  customerId: number
  customerName: string
  ownerId: number
  ownerName: string
  direction: string
  quantity: number
  price: number
  dealQuantity: number
  amount: number
  feeRate: number
  feeAmount: number
  totalAmount: number
  status: string
  createdAt: string
}

export interface OrderLogRow {
  id: number
  orderId: number
  fromStatus: string | null
  toStatus: string
  operatorId: number
  reason: string
  createdAt: string
}

export interface OrderSettleRow {
  id: number
  remittanceId: number
  remitNo: string
  amount: number
  operatorName: string
  createdAt: string
}

export interface OrderDetail extends OrderRow {
  paidAmount: number
  confirmedAt: string
  cancelledAt: string
  cancelReason: string
  remark: string
  logs: OrderLogRow[]
  settlements: OrderSettleRow[]
}

export interface OrderSave {
  customerId: number
  itemId: number
  direction: string
  quantity: number
  price?: number
}

export function pageOrders(params: {
  keyword?: string
  status?: string
  direction?: string
  customerId?: number
  pageNum?: number
  pageSize?: number
}): Promise<PageData<OrderRow>> {
  return request.get('/trade/orders', { params })
}

export function orderDetail(id: number): Promise<OrderDetail> {
  return request.get(`/trade/orders/${id}`)
}

export function createOrder(data: OrderSave): Promise<number> {
  return request.post('/trade/orders', data)
}

export function approveOrder(id: number, reason?: string): Promise<void> {
  return request.post(`/trade/orders/${id}/approve`, reason ? { reason } : {})
}

export function rejectOrder(id: number, reason: string): Promise<void> {
  return request.post(`/trade/orders/${id}/reject`, { reason })
}

export function cancelOrder(id: number, reason?: string): Promise<void> {
  return request.post(`/trade/orders/${id}/cancel`, reason ? { reason } : {})
}

// ==================== REM 汇款核销（RM-1~4） ====================

export interface RemitRow {
  id: number
  remitNo: string
  customerId: number
  customerName: string
  ownerId: number
  ownerName: string
  amount: number
  currency: string
  remittedAt: string
  voucherKey: string
  status: string
  writtenOffAmount: number
  balance: number
  confirmedAt: string
  rejectReason: string
  remark: string
  createdAt: string
}

export interface RemitDetail extends RemitRow {
  confirmerName: string
  settlements: OrderSettleRow[]
}

export interface RemitSave {
  remitNo: string
  customerId: number
  amount: number
  currency?: string
  remittedAt: string
  voucherKey?: string
  remark?: string
}

export function pageRemits(params: {
  keyword?: string
  status?: string
  customerId?: number
  pageNum?: number
  pageSize?: number
}): Promise<PageData<RemitRow>> {
  return request.get('/trade/remit', { params })
}

export function remitDetail(id: number): Promise<RemitDetail> {
  return request.get(`/trade/remit/${id}`)
}

export function registerRemit(data: RemitSave): Promise<number> {
  return request.post('/trade/remit', data)
}

export function confirmRemit(id: number): Promise<void> {
  return request.post(`/trade/remit/${id}/confirm`)
}

export function rejectRemit(id: number, reason: string): Promise<void> {
  return request.post(`/trade/remit/${id}/reject`, { reason })
}

export function writeOffRemit(id: number, items: { orderId: number; amount: number }[]): Promise<void> {
  return request.post(`/trade/remit/${id}/write-off`, items)
}

// ==================== RTP 通知动态流（轮询兜底） ====================

export interface FeedRow {
  id: number
  type: string
  title: string
  content: string
  relType: string
  relId: number
  read: boolean
  createdAt: string
}

export function notifyUnreadCount(): Promise<number> {
  return request.get('/trade/notify/unread-count')
}

export function notifyFeed(pageNum = 1, pageSize = 20): Promise<PageData<FeedRow>> {
  return request.get('/trade/notify/feed', { params: { pageNum, pageSize } })
}

export function notifyRead(id: number): Promise<void> {
  return request.post(`/trade/notify/${id}/read`)
}

export function notifyReadAll(): Promise<void> {
  return request.post('/trade/notify/read-all')
}

// ==================== WSP 工作台聚合 ====================

export interface WorkbenchData {
  scope: string
  cards: {
    myOrderCount: number
    myOrderAmount: number
    myMonthAmount: number
    scopeOrderAmount: number
    pendingApproveCount: number
    pendingRemitCount: number
    unreadCount: number
  }
  statusStats: Record<string, number>
  pendingApprovals: OrderRow[]
  pendingRemits: RemitRow[]
  recentFeed: FeedRow[]
}

export interface TimelineEvent {
  time: string
  type: string
  content: string
  relType: string
  relId: number
}

export interface CustomerPanel {
  customerName: string
  orders: OrderRow[]
  remittances: RemitRow[]
  timeline: TimelineEvent[]
}

export function tradeWorkbench(): Promise<WorkbenchData> {
  return request.get('/trade/workbench')
}

export function tradeCustomerPanel(customerId: number): Promise<CustomerPanel> {
  return request.get(`/trade/workbench/customer/${customerId}`)
}
