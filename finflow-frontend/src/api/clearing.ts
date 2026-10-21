import request from './request'
import type { ClearingOrder, ClearingOrderDetail, PageResponse } from '../types/api'

/** 清分单分页查询。 */
export function pageClearings(params: {
  page: number
  size: number
  platformOrderNo?: string
  merchantId?: number | null
  settleDate?: string | null
}) {
  return request.get<unknown, PageResponse<ClearingOrder>>('/clearings', { params })
}

/** 清分单详情（含分账明细）。 */
export function getClearingDetail(id: number) {
  return request.get<unknown, ClearingOrderDetail>(`/clearings/${id}`)
}
