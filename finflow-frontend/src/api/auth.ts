import request from './request'
import type { CaptchaInfo, MePerms, MenuNode, PageResponse, TokenPair, UserProfile } from '@/types/api'

/** 登录请求体。 */
export interface LoginPayload {
  username: string
  password: string
  captchaId: string
  captchaCode: string
}

/**
 * 请求方法泛型约定：<unknown, 业务类型>。
 * 响应拦截器已统一拆包，第二泛型即业务方法实际拿到的类型。
 */

/** 认证接口。 */
export const authApi = {
  captcha: () => request.get<unknown, CaptchaInfo>('/auth/captcha'),

  login: (payload: LoginPayload) => request.post<unknown, TokenPair>('/auth/login', payload),

  logout: (refreshToken: string) => request.post<unknown, void>('/auth/logout', { refreshToken }),

  me: () => request.get<unknown, UserProfile>('/auth/me'),

  myPerms: () => request.get<unknown, MePerms>('/auth/me/perms'),

  /** 本人可见菜单树：侧边栏与动态路由的数据源。 */
  myMenus: () => request.get<unknown, MenuNode[]>('/auth/me/menus'),

  /** 本人登录日志。 */
  myLoginLogs: (page: number, size: number) =>
    request.get<unknown, PageResponse<LoginLogItem>>('/auth/login-logs', { params: { page, size } }),

  /** 本人登录设备列表。 */
  mySessions: () => request.get<unknown, SessionItem[]>('/auth/sessions'),
}

/** 登录日志条目。 */
export interface LoginLogItem {
  id: number
  username: string
  success: boolean
  failReason: string | null
  ip: string | null
  userAgent: string | null
  createdAt: string
}

/** 登录会话（活跃 refresh token）。 */
export interface SessionItem {
  id: number
  deviceName: string | null
  ip: string | null
  createdAt: string
}
