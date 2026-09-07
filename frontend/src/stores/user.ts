import { defineStore } from 'pinia'

/**
 * 用户会话 Store（Pinia）
 * SYS-DV-02 扩展：permissions（菜单/按钮权限过滤）与 dataScope（口径提示）持久化
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('crm_token') || '',
    username: localStorage.getItem('crm_username') || '',
    permissions: JSON.parse(localStorage.getItem('crm_permissions') || '[]') as string[],
    dataScope: localStorage.getItem('crm_data_scope') || 'SELF',
  }),
  getters: {
    /** 系统管理菜单可见性：任一系统管理权限点 */
    canAccessSystem: (state) =>
      state.permissions.some((p) => p.startsWith('system:')),
  },
  actions: {
    hasPerm(code: string): boolean {
      return this.permissions.includes(code)
    },
    hasAnyPerm(codes: string[]): boolean {
      return codes.some((c) => this.permissions.includes(c))
    },
    setSession(token: string, username: string, permissions: string[] = [], dataScope = 'SELF') {
      this.token = token
      this.username = username
      this.permissions = permissions
      this.dataScope = dataScope
      localStorage.setItem('crm_token', token)
      localStorage.setItem('crm_username', username)
      localStorage.setItem('crm_permissions', JSON.stringify(permissions))
      localStorage.setItem('crm_data_scope', dataScope)
    },
    clear() {
      this.token = ''
      this.username = ''
      this.permissions = []
      this.dataScope = 'SELF'
      localStorage.removeItem('crm_token')
      localStorage.removeItem('crm_username')
      localStorage.removeItem('crm_permissions')
      localStorage.removeItem('crm_data_scope')
    },
  },
})
