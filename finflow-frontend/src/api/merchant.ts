import request from './request'
import type {
  ApiKeyCreated,
  Merchant,
  MerchantApiKey,
  MerchantDetail,
  MerchantRate,
  MerchantRateLog,
  PageResponse,
  SettleAccount,
} from '@/types/api'

/**
 * 商户中心 API：商户主体 / 凭证 / 费率 / 结算账户。
 * 请求方法泛型约定：<unknown, 业务类型>，响应拦截器已统一拆包。
 */
export const merchantApi = {
  page: (page: number, size: number, params?: { status?: string; keyword?: string }) =>
    request.get<unknown, PageResponse<Merchant>>('/merchants', { params: { page, size, ...params } }),

  detail: (id: number) => request.get<unknown, MerchantDetail>(`/merchants/${id}`),

  create: (payload: {
    merchantName: string
    category?: string
    contactName: string
    contactPhone: string
    contactEmail?: string
    businessLicenseNo: string
    businessAddress?: string
    settleCycle?: string
  }) => request.post<unknown, Merchant>('/merchants', payload),

  update: (
    id: number,
    payload: {
      merchantName: string
      category?: string
      contactName: string
      contactPhone: string
      contactEmail?: string
      businessLicenseNo: string
      businessAddress?: string
      settleCycle?: string
      webhookUrl?: string
    },
  ) => request.put<unknown, Merchant>(`/merchants/${id}`, payload),

  changeStatus: (id: number, targetStatus: string, reason?: string) =>
    request.post<unknown, Merchant>(`/merchants/${id}/status`, { targetStatus, reason }),

  review: (id: number, approve: boolean, opinion?: string) =>
    request.post<unknown, Merchant>(`/merchants/${id}/review`, { approve, opinion }),

  /** ---------- API 凭证 ---------- */

  apiKeys: (merchantId: number) =>
    request.get<unknown, MerchantApiKey[]>(`/merchants/${merchantId}/api-keys`),

  createApiKey: (merchantId: number) =>
    request.post<unknown, ApiKeyCreated>(`/merchants/${merchantId}/api-keys`),

  updateIpWhitelist: (merchantId: number, keyId: number, ips: string[]) =>
    request.put<unknown, MerchantApiKey>(`/merchants/${merchantId}/api-keys/${keyId}/ip-whitelist`, { ips }),

  /** ---------- 费率 ---------- */

  rate: (merchantId: number) => request.get<unknown, MerchantRate | null>(`/merchants/${merchantId}/rate`),

  setRate: (merchantId: number, payload: { rateBps?: number; fixedFee?: number }) =>
    request.put<unknown, MerchantRate>(`/merchants/${merchantId}/rate`, payload),

  rateLogs: (merchantId: number) =>
    request.get<unknown, MerchantRateLog[]>(`/merchants/${merchantId}/rate-logs`),

  /** ---------- 结算账户 ---------- */

  settleAccounts: (merchantId: number) =>
    request.get<unknown, SettleAccount[]>(`/merchants/${merchantId}/settle-accounts`),

  createSettleAccount: (
    merchantId: number,
    payload: { accountType: string; accountName: string; accountNo: string; bankName?: string },
  ) => request.post<unknown, SettleAccount>(`/merchants/${merchantId}/settle-accounts`, payload),

  deleteSettleAccount: (merchantId: number, accountId: number) =>
    request.delete<unknown, void>(`/merchants/${merchantId}/settle-accounts/${accountId}`),

  markDefaultAccount: (merchantId: number, accountId: number) =>
    request.put<unknown, SettleAccount>(`/merchants/${merchantId}/settle-accounts/${accountId}/default`),
}
