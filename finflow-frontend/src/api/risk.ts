import request from './request'
import type { PageResponse, PaymentOrder, RiskBlacklist, RiskEvent, RiskRule } from '@/types/api'

/** 规则新增/修改请求体（remark 可空）。 */
export interface RulePayload {
  ruleName: string
  ruleType: string
  params: string
  score: number
  priority: number
  enabled: boolean
  remark?: string | null
}

/**
 * 风控中心 API：规则管理（动态增删改 + 启停）、黑名单、风险事件、REVIEW 订单人工处理。
 * 规则改动下一笔交易即时生效（后端求值每次实时读库）。金额单位为分。
 */
export const riskApi = {
  rulePage: (page: number, size: number, params?: { ruleName?: string; ruleType?: string; enabled?: boolean }) =>
    request.get<unknown, PageResponse<RiskRule>>('/risk/rules', { params: { page, size, ...params } }),

  ruleCreate: (data: RulePayload) => request.post<unknown, RiskRule>('/risk/rules', data),

  ruleUpdate: (id: number, data: RulePayload) => request.put<unknown, RiskRule>(`/risk/rules/${id}`, data),

  ruleToggle: (id: number, enabled: boolean) =>
    request.put<unknown, RiskRule>(`/risk/rules/${id}/status`, { enabled }),

  ruleDelete: (id: number) => request.delete<unknown, void>(`/risk/rules/${id}`),

  blacklistPage: (page: number, size: number, params?: { dimension?: string; value?: string }) =>
    request.get<unknown, PageResponse<RiskBlacklist>>('/risk/blacklists', { params: { page, size, ...params } }),

  blacklistCreate: (data: { dimension: string; value: string; reason?: string }) =>
    request.post<unknown, RiskBlacklist>('/risk/blacklists', data),

  blacklistDelete: (id: number) => request.delete<unknown, void>(`/risk/blacklists/${id}`),

  eventPage: (
    page: number,
    size: number,
    params?: { orderId?: number; merchantId?: number; verdict?: string; createdFrom?: string; createdTo?: string },
  ) => request.get<unknown, PageResponse<RiskEvent>>('/risk/events', { params: { page, size, ...params } }),

  /** 人工通过：订单回 PENDING 并直接渠道路由（不重查风控）。 */
  approveOrder: (orderId: number, remark?: string) =>
    request.post<unknown, void>(`/risk/orders/${orderId}/approve`, { remark }),

  /** 人工拒绝：订单置 FAILED。 */
  rejectOrder: (orderId: number, remark?: string) =>
    request.post<unknown, void>(`/risk/orders/${orderId}/reject`, { remark }),
}

/** 风险订单列表复用支付订单分页接口（按 status=REVIEW 筛选）。 */
export const riskOrderApi = {
  page: (page: number, size: number, keyword?: string) =>
    request.get<unknown, PageResponse<PaymentOrder>>('/payments/orders', {
      params: { page, size, status: 'REVIEW', keyword: keyword || undefined },
    }),
}
