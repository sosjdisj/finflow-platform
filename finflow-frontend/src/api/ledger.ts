import request from './request'
import type { Account, LedgerConsistency, LedgerRecord, PageResponse } from '@/types/api'

/**
 * 资金中心 API：账户查询 / 资金流水查询 / 人工调账 / 一致性校验。
 * 金额单位为分（铁律 1）；时间条件为本地时间 ISO 字符串（yyyy-MM-ddTHH:mm:ss）。
 */
export const ledgerApi = {
  accountPage: (
    page: number,
    size: number,
    params?: { ownerType?: string; currency?: string },
  ) => request.get<unknown, PageResponse<Account>>('/accounts', { params: { page, size, ...params } }),

  recordPage: (
    page: number,
    size: number,
    params?: {
      accountId?: number
      bizType?: string
      bizNo?: string
      createdFrom?: string
      createdTo?: string
    },
  ) => request.get<unknown, PageResponse<LedgerRecord>>('/ledger/records', { params: { page, size, ...params } }),

  /** 人工调账：amount 带符号（正调增、负调减），单位分。 */
  adjust: (data: { accountId: number; amount: number; remark?: string }) =>
    request.post<unknown, LedgerRecord>('/ledger/adjustments', data),

  consistency: (accountId: number) =>
    request.get<unknown, LedgerConsistency>(`/ledger/accounts/${accountId}/consistency`),
}
