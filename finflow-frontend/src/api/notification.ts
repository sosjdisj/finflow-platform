import request from './request'
import type { NotificationItem, PageResponse } from '../types/api'

/** 通知记录分页查询。 */
export function pageNotifications(params: {
  page: number
  size: number
  merchantId?: number | null
  templateCode?: string | null
  emailStatus?: string | null
}) {
  return request.get<unknown, PageResponse<NotificationItem>>('/notifications', { params })
}

/** 未读站内信数（铃铛角标）。 */
export function getUnreadCount() {
  return request.get<unknown, number>('/notifications/unread-count')
}

/** 标记单条已读（幂等，已读静默跳过）。 */
export function markNotificationRead(id: number) {
  return request.post<unknown, void>(`/notifications/${id}/read`)
}

/** 全部标记已读，返回本次标记条数。 */
export function markAllNotificationsRead() {
  return request.post<unknown, number>('/notifications/read-all')
}
