import request from './request'
import type { MenuNode, PageResponse, Permission, Role, SystemConfigItem, UserAdmin } from '@/types/api'

/**
 * 请求方法泛型约定：<unknown, 业务类型>。
 * 响应拦截器已统一拆包，第二泛型即业务方法实际拿到的类型。
 */

/** ---------- 用户管理 ---------- */

export const userApi = {
  page: (page: number, size: number, keyword?: string) =>
    request.get<unknown, PageResponse<UserAdmin>>('/system/users', { params: { page, size, keyword } }),

  create: (payload: { username: string; password: string; nickname?: string; email?: string; phone?: string }) =>
    request.post<unknown, UserAdmin>('/system/users', payload),

  update: (id: number, payload: { nickname?: string; email?: string; phone?: string }) =>
    request.put<unknown, UserAdmin>(`/system/users/${id}`, payload),

  updateStatus: (id: number, status: number) =>
    request.put<unknown, UserAdmin>(`/system/users/${id}/status`, { status }),

  resetPassword: (id: number, newPassword: string) =>
    request.put<unknown, void>(`/system/users/${id}/password`, { newPassword }),

  /** 用户的角色 ID 列表。 */
  roles: (id: number) => request.get<unknown, { userId: number; roleIds: number[] }>(`/system/users/${id}/roles`),

  /** 全量覆盖分配角色。 */
  assignRoles: (id: number, roleIds: number[]) =>
    request.put<unknown, void>(`/system/users/${id}/roles`, { roleIds }),
}

/** ---------- 角色管理 ---------- */

export const roleApi = {
  page: (page: number, size: number) =>
    request.get<unknown, PageResponse<Role>>('/system/roles', { params: { page, size } }),

  create: (payload: { code: string; name: string; description?: string }) =>
    request.post<unknown, Role>('/system/roles', payload),

  update: (id: number, payload: { name: string; description?: string; status: number }) =>
    request.put<unknown, Role>(`/system/roles/${id}`, payload),

  remove: (id: number) => request.delete<unknown, void>(`/system/roles/${id}`),

  /** 角色已分配的权限 ID 列表。 */
  permissions: (id: number) =>
    request.get<unknown, { roleId: number; permissionIds: number[] }>(`/system/roles/${id}/permissions`),

  /** 全量覆盖分配权限。 */
  assignPermissions: (id: number, permissionIds: number[]) =>
    request.put<unknown, void>(`/system/roles/${id}/permissions`, { permissionIds }),
}

/** ---------- 权限点管理 ---------- */

export const permissionApi = {
  page: (page: number, size: number) =>
    request.get<unknown, PageResponse<Permission>>('/system/permissions', { params: { page, size } }),

  create: (payload: { code: string; name: string; type: number; description?: string }) =>
    request.post<unknown, Permission>('/system/permissions', payload),

  update: (id: number, payload: { name: string; description?: string; status: number }) =>
    request.put<unknown, Permission>(`/system/permissions/${id}`, payload),

  remove: (id: number) => request.delete<unknown, void>(`/system/permissions/${id}`),
}

/** ---------- 菜单管理 ---------- */

export interface MenuPayload {
  parentId: number
  title: string
  path: string
  icon: string | null
  menuType: number
  sortNo: number
  permissionId: number | null
  visible: number
}

export const menuApi = {
  tree: () => request.get<unknown, MenuNode[]>('/system/menus/tree'),

  create: (payload: MenuPayload) => request.post<unknown, MenuNode>('/system/menus', payload),

  update: (id: number, payload: MenuPayload) => request.put<unknown, MenuNode>(`/system/menus/${id}`, payload),

  remove: (id: number) => request.delete<unknown, void>(`/system/menus/${id}`),
}

/** ---------- 系统配置（S37） ---------- */

export const configApi = {
  list: () => request.get<unknown, SystemConfigItem[]>('/system/configs'),

  update: (key: string, payload: { configValue: string; description?: string }) =>
    request.put<unknown, SystemConfigItem>(`/system/configs/${key}`, payload),
}
