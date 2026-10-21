/** 商户状态展示映射：两个页面共用，唯一事实源。 */
export const MERCHANT_STATUS_LABEL: Record<string, string> = {
  PENDING: '待审核',
  REVIEWING: '审核中',
  ACTIVE: '生效中',
  SUSPENDED: '已暂停',
  CLOSED: '已关闭',
}

export const MERCHANT_STATUS_TAG: Record<string, 'info' | 'primary' | 'success' | 'warning' | 'danger'> = {
  PENDING: 'warning',
  REVIEWING: 'primary',
  ACTIVE: 'success',
  SUSPENDED: 'info',
  CLOSED: 'danger',
}

export const ACCOUNT_TYPE_LABEL: Record<string, string> = {
  BANK_CARD: '银行卡',
  ALIPAY: '支付宝',
  WECHAT: '微信',
}

/** 万分比 → 百分比展示（60 → 0.6%）。 */
export function bpsToPercent(bps: number | null): string {
  return bps == null ? '-' : `${bps / 100}%`
}

/** 分 → 元展示（100 → ¥1.00）。 */
export function feeToYuan(fee: number | null): string {
  return fee == null ? '-' : `¥${(fee / 100).toFixed(2)}`
}
