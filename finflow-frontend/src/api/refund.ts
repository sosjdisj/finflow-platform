import request from './request'
import type { PageResponse, RefundOrder, RefundOrderDetail } from '@/types/api'

/**
 * 退款管理 API。金额单位为分；时间条件为本地时间 ISO 字符串（yyyy-MM-ddTHH:mm:ss）。
 */
export const refundApi = {
  page: (
    page: number,
    size: number,
    params?: {
      orderId?: number
      merchantId?: number
      refundNo?: string
      status?: string
      createdFrom?: string
      createdTo?: string
    },
  ) => request.get<unknown, PageResponse<RefundOrder>>('/refunds', { params: { page, size, ...params } }),

  detail: (id: number) => request.get<unknown, RefundOrderDetail>(`/refunds/${id}`),

  create: (data: { orderId: number; amount: number; reason?: string }) =>
    request.post<unknown, RefundOrder>('/refunds', data),
}
