import request from './request'
import type { PageResponse, SettlementOrder, SettlementOrderDetail } from '../types/api'

/** 结算单分页查询。 */
export function pageSettlements(params: {
  page: number
  size: number
  settleNo?: string
  merchantId?: number | null
  settleDate?: string | null
  status?: string | null
}) {
  return request.get<unknown, PageResponse<SettlementOrder>>('/settlements', { params })
}

/** 结算单详情（含逐笔明细与状态留痕）。 */
export function getSettlementDetail(id: number) {
  return request.get<unknown, SettlementOrderDetail>(`/settlements/${id}`)
}

/** 审核通过：CALCULATED → APPROVED。 */
export function approveSettlement(id: number) {
  return request.post<unknown, void>(`/settlements/${id}/approve`)
}

/** 执行打款（FAILED 单可重复调用重试）。 */
export function executeSettlement(id: number) {
  return request.post<unknown, void>(`/settlements/${id}/execute`)
}
