/**
 * 退款单展示常量：状态中文映射与标签颜色。
 * 状态集合与后端 RefundStatus 枚举一一对应（单一事实源在后端）。
 */

/** 退款单状态标签。 */
export const REFUND_STATUS_LABEL: Record<string, string> = {
  APPLYING: '申请中',
  PROCESSING: '退款处理中',
  SUCCESS: '退款成功',
  FAILED: '退款失败',
  CLOSED: '已关闭',
}

/** 退款单状态标签颜色。 */
export const REFUND_STATUS_TAG: Record<string, string> = {
  APPLYING: 'info',
  PROCESSING: 'warning',
  SUCCESS: 'success',
  FAILED: 'danger',
  CLOSED: 'info',
}
