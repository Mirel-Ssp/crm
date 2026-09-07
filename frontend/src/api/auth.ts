import request from './request'

/**
 * 认证接口（对接后端 /api/auth/**，INF-DV-03；SYS-DV-02 扩展权限字段）
 */
export interface UserInfo {
  id: number
  username: string
  realName: string
  orgId: number
  /** 权限点编码集（如 customer:list / system:user） */
  permissions: string[]
  /** 最宽数据范围 SELF / TEAM / ALL */
  dataScope: string
  /** 1=首登/被重置后需强制改密（批次3） */
  mustChangePassword?: number
}

export interface LoginResp {
  accessToken: string
  refreshToken: string
  expiresInMinutes: number
  userInfo: UserInfo
}

export function login(username: string, password: string): Promise<LoginResp> {
  return request.post('/auth/login', { username, password })
}

export function refresh(refreshToken: string): Promise<LoginResp> {
  return request.post('/auth/refresh', { refreshToken })
}

export function fetchMe(): Promise<UserInfo> {
  return request.get('/auth/me')
}

/** 自助改密（验旧密 + 复位首登强改标记） */
export function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  return request.post('/auth/password', { oldPassword, newPassword })
}
