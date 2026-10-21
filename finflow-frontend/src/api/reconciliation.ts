import request from './request'
import type {
  MatchSummary,
  PageResponse,
  ReconciliationDifference,
  ReconciliationRecord,
  ReconciliationTask,
} from '@/types/api'

/** 对账中心 API：任务上传/查询/匹配、匹配结果查询、差异单处理流。金额单位为分。 */
export const reconciliationApi = {
  /** 上传渠道账单 CSV（表单：file + channelCode + billDate）。 */
  upload: (file: File, channelCode: string, billDate: string) => {
    const form = new FormData()
    form.append('file', file)
    form.append('channelCode', channelCode)
    form.append('billDate', billDate)
    return request.post<unknown, ReconciliationTask>('/reconciliation/tasks', form)
  },

  taskPage: (
    page: number,
    size: number,
    params?: { taskNo?: string; channelCode?: string; billDate?: string; status?: string },
  ) => request.get<unknown, PageResponse<ReconciliationTask>>('/reconciliation/tasks', { params: { page, size, ...params } }),

  taskDetail: (taskId: number) =>
    request.get<unknown, ReconciliationTask>(`/reconciliation/tasks/${taskId}`),

  /** 执行匹配（可重跑覆盖），返回五类结果汇总。 */
  match: (taskId: number) =>
    request.post<unknown, MatchSummary>(`/reconciliation/tasks/${taskId}/match`),

  /** 匹配结果分页（对账结果页数据源），可按五类结果筛选。 */
  recordPage: (taskId: number, page: number, size: number, result?: string) =>
    request.get<unknown, PageResponse<ReconciliationRecord>>(`/reconciliation/tasks/${taskId}/records`, {
      params: { page, size, result: result || undefined },
    }),

  /** 由匹配记录发起差异单（仅非 MATCHED 记录）。 */
  createDifference: (recordId: number) =>
    request.post<unknown, ReconciliationDifference>('/reconciliation/differences', { recordId }),

  differencePage: (
    page: number,
    size: number,
    params?: { diffNo?: string; taskId?: number; diffType?: string; status?: string; merchantOrderNo?: string },
  ) =>
    request.get<unknown, PageResponse<ReconciliationDifference>>('/reconciliation/differences', {
      params: { page, size, ...params },
    }),

  differenceDetail: (id: number) =>
    request.get<unknown, ReconciliationDifference>(`/reconciliation/differences/${id}`),

  /** 审核确认：选定处理方式 ADJUST（调账）/ SUPPLEMENT（补单）。 */
  confirmDifference: (id: number, handleType: string, handleRemark: string) =>
    request.post<unknown, ReconciliationDifference>(`/reconciliation/differences/${id}/confirm`, {
      handleType,
      handleRemark,
    }),

  ignoreDifference: (id: number, remark: string) =>
    request.post<unknown, ReconciliationDifference>(`/reconciliation/differences/${id}/ignore`, { remark }),

  /**
   * 执行处理（成功即关闭）。ADJUST：accountId + 带符号金额（分）；SUPPLEMENT：handleResult 凭证。
   */
  handleDifference: (id: number, data: { accountId?: number; amount?: number; handleResult?: string }) =>
    request.post<unknown, ReconciliationDifference>(`/reconciliation/differences/${id}/handle`, data),
}
