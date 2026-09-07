import request from './request'

/**
 * 系统管理 API（SYS-DV-01/05：组织/成员/角色/字典）
 */

// ---------- 组织 ----------
export interface OrgNode {
  id: number
  parentId: number
  name: string
  leaderId: number
  sort: number
  children: OrgNode[]
}

export interface OrgSaveReq {
  parentId: number
  name: string
  leaderId?: number | null
  sort?: number
}

export function orgTree(): Promise<OrgNode[]> {
  return request.get('/system/orgs/tree')
}

export function createOrg(data: OrgSaveReq): Promise<number> {
  return request.post('/system/orgs', data)
}

export function updateOrg(id: number, data: OrgSaveReq): Promise<void> {
  return request.put(`/system/orgs/${id}`, data)
}

export function deleteOrg(id: number): Promise<void> {
  return request.delete(`/system/orgs/${id}`)
}

// ---------- 成员 ----------
export interface UserRow {
  id: number
  username: string
  realName: string
  orgId: number
  orgName: string
  email: string
  phone: string
  status: string
  roleIds: number[]
  roleNames: string[]
  createdAt: string
}

export interface UserQuery {
  keyword?: string
  orgId?: number
  status?: string
  pageNum?: number
  pageSize?: number
}

export interface UserCreateReq {
  orgId: number
  username: string
  password: string
  realName: string
  email?: string
  phone?: string
  roleIds?: number[]
}

export interface UserUpdateReq {
  orgId: number
  realName: string
  /** 留空 = 不变更（防脱敏回写） */
  email?: string
  phone?: string
  status?: string
}

export function pageUsers(params: UserQuery): Promise<PageResp<UserRow>> {
  return request.get('/system/users', { params })
}

export function createUser(data: UserCreateReq): Promise<number> {
  return request.post('/system/users', data)
}

export function updateUser(id: number, data: UserUpdateReq): Promise<void> {
  return request.put(`/system/users/${id}`, data)
}

export function assignUserRoles(id: number, roleIds: number[]): Promise<void> {
  return request.put(`/system/users/${id}/roles`, { roleIds })
}

export function resetUserPassword(id: number, password: string): Promise<void> {
  return request.put(`/system/users/${id}/password`, { password })
}

export function deleteUser(id: number): Promise<void> {
  return request.delete(`/system/users/${id}`)
}

// ---------- 角色 ----------
export interface RoleRow {
  id: number
  code: string
  name: string
  dataScope: string
  remark: string
  permissionIds: number[]
}

export interface PermissionRow {
  id: number
  code: string
  name: string
  type: string
}

export interface RoleSaveReq {
  code: string
  name: string
  dataScope: string
  remark?: string
}

export function listRoles(): Promise<RoleRow[]> {
  return request.get('/system/roles')
}

export function listPermissions(): Promise<PermissionRow[]> {
  return request.get('/system/roles/permissions')
}

export function createRole(data: RoleSaveReq): Promise<number> {
  return request.post('/system/roles', data)
}

export function updateRole(id: number, data: RoleSaveReq): Promise<void> {
  return request.put(`/system/roles/${id}`, data)
}

export function assignRolePermissions(id: number, permissionIds: number[]): Promise<void> {
  return request.put(`/system/roles/${id}/permissions`, { permissionIds })
}

export function deleteRole(id: number): Promise<void> {
  return request.delete(`/system/roles/${id}`)
}

// ---------- 字典 ----------
export interface DictRow {
  id: number
  dictType: string
  code: string
  value: string
  sort: number
}

export interface DictSaveReq {
  dictType: string
  code: string
  value: string
  sort?: number
}

export function listDicts(type?: string): Promise<DictRow[]> {
  return request.get('/dicts', { params: type ? { type } : {} })
}

export function createDict(data: DictSaveReq): Promise<number> {
  return request.post('/dicts', data)
}

export function updateDict(id: number, data: DictSaveReq): Promise<void> {
  return request.put(`/dicts/${id}`, data)
}

export function deleteDict(id: number): Promise<void> {
  return request.delete(`/dicts/${id}`)
}

// ---------- 通用分页响应（对应后端 PageResult：字段为 list） ----------
export interface PageResp<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}
