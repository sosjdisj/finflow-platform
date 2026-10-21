import request from './request'
import type { PageResponse, WebhookEvent, WebhookEventDetail } from '../types/api'

/** Webhook 事件分页查询。 */
export function pageWebhookEvents(params: {
  page: number
  size: number
  merchantId?: number | null
  status?: string | null
  eventType?: string | null
}) {
  return request.get<unknown, PageResponse<WebhookEvent>>('/webhooks/events', { params })
}

/** Webhook 事件详情（报文全文 + 投递尝试记录）。 */
export function getWebhookEventDetail(id: number) {
  return request.get<unknown, WebhookEventDetail>(`/webhooks/events/${id}`)
}

/** 手动重试：重置计数后立即投递一次，返回投递后的最新事件状态。 */
export function retryWebhookEvent(id: number) {
  return request.post<unknown, WebhookEvent>(`/webhooks/events/${id}/retry`)
}
