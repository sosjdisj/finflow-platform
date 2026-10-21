import request from './request'
import type { PageResponse, PaymentOrder, PaymentOrderDetail } from '@/types/api'

/**
 * 支付中心 API：支付订单查询 + 代客下单。
 * 金额条件单位为分；时间条件为本地时间 ISO 字符串（yyyy-MM-ddTHH:mm:ss）。
 */
export const paymentApi = {
  /** 代客下单：与商户侧同一下单接口，请求头 Idempotency-Key 由 request 拦截器统一注入。 */
  create: (data: {
    merchantId: number
    merchantOrderNo: string
    amount: number
    currency?: string
    subject: string
    paymentMethod: string
    callbackUrl?: string
    expiredAt?: string
  }) => request.post<unknown, PaymentOrder>('/payments/orders', data),

  page: (
    page: number,
    size: number,
    params?: {
      merchantId?: number
      status?: string
      keyword?: string
      createdFrom?: string
      createdTo?: string
      minAmount?: number
      maxAmount?: number
    },
  ) => request.get<unknown, PageResponse<PaymentOrder>>('/payments/orders', { params: { page, size, ...params } }),

  detail: (id: number) => request.get<unknown, PaymentOrderDetail>(`/payments/orders/${id}`),
}
