/**
 * 支付订单展示常量与格式化：状态 / 支付方式 / 交易类型中文映射、金额分转元。
 * 状态集合与后端 PaymentStatus 枚举一一对应（单一事实源在后端）。
 */

/** 支付订单状态标签。 */
export const PAYMENT_STATUS_LABEL: Record<string, string> = {
  CREATED: '已创建',
  PENDING: '待支付',
  PROCESSING: '处理中',
  PAID: '已支付',
  SETTLED: '已结算',
  FAILED: '支付失败',
  CLOSED: '已关闭',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
}

/** 支付订单状态标签颜色。 */
export const PAYMENT_STATUS_TAG: Record<string, string> = {
  CREATED: 'info',
  PENDING: 'warning',
  PROCESSING: 'primary',
  PAID: 'success',
  SETTLED: 'success',
  FAILED: 'danger',
  CLOSED: 'info',
  REFUNDING: 'warning',
  REFUNDED: 'info',
}

/** 支付方式标签（与渠道配置 payment_methods 取值一致）。 */
export const PAYMENT_METHOD_LABEL: Record<string, string> = {
  ALIPAY: '支付宝',
  WECHAT: '微信支付',
  BANK_CARD: '银行卡',
  QUICK_CARD: '快捷支付',
}

/** 渠道交互类型标签。 */
export const TXN_TYPE_LABEL: Record<string, string> = {
  PAYMENT: '支付',
  REFUND: '退款',
  QUERY: '支付查询',
  REFUND_QUERY: '退款查询',
}

/** 渠道交互报文（JSON 字符串）格式化，供折叠展示；解析失败原样返回。 */
export const prettyPayload = (payload: string | null): string => {
  if (!payload) {
    return '—'
  }
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}
