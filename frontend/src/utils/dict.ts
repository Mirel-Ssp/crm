/**
 * 数据字典常量（与 V2 种子 sys_dict 对应）
 * 字典接口版（SYS-DV-02）就绪后切换为动态加载
 */

export const CUSTOMER_LEVELS = [
  { value: 'VIP', label: 'VIP 客户' },
  { value: 'IMPORTANT', label: '重要客户' },
  { value: 'NORMAL', label: '普通客户' },
] as const

export const CUSTOMER_STATUSES = [
  { value: 'ACTIVE', label: '活跃' },
  { value: 'COOPERATING', label: '已合作' },
  { value: 'INACTIVE', label: '休眠' },
] as const

export const FOLLOWUP_METHODS = [
  { value: 'PHONE', label: '电话' },
  { value: 'VISIT', label: '拜访' },
  { value: 'WECHAT', label: '微信' },
  { value: 'EMAIL', label: '邮件' },
  { value: 'OTHER', label: '其他' },
] as const

export function labelOf(list: readonly { value: string; label: string }[], value: string | undefined | null): string {
  return list.find((x) => x.value === value)?.label ?? value ?? '-'
}
