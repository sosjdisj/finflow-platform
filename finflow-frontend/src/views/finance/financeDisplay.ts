/**
 * 资金中心展示常量：主体类型 / 业务类型 / 来源中文映射、账户类型标签色。
 * 取值集合与后端枚举及迁移初始化数据一一对应（单一事实源在后端）。
 */

/** 账户主体类型标签。 */
export const OWNER_TYPE_LABEL: Record<string, string> = {
  MERCHANT: '商户',
  PLATFORM: '平台',
  CHANNEL: '渠道',
}

/** 账户主体类型标签颜色。 */
export const OWNER_TYPE_TAG: Record<string, string> = {
  MERCHANT: 'primary',
  PLATFORM: 'warning',
  CHANNEL: 'info',
}

/** 流水业务类型标签。 */
export const BIZ_TYPE_LABEL: Record<string, string> = {
  PAYMENT: '支付入账',
  SETTLEMENT: '结算',
  ADJUSTMENT: '调账',
}

/** 流水来源标签。 */
export const SOURCE_LABEL: Record<string, string> = {
  PAYMENT_EVENT: '支付事件',
  CONSOLE: '运营后台',
  TASK: '定时任务',
}
