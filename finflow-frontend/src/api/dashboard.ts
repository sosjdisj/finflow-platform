import request from './request'

/** Dashboard 今日汇总（金额单位：分）。 */
export interface DashboardSummary {
  todayTradeAmount: number
  todayOrderCount: number
  /** 百分比 0~100。 */
  successRate: number
  todayRefundAmount: number
  todaySettlementAmount: number
  todayPlatformFee: number
  todayRiskRejectCount: number
  todayAbnormalOrderCount: number
}

/** 按日金额点。 */
export interface DailyAmount {
  date: string
  amount: number
}

/** 按日成功率点（百分比）。 */
export interface DailyRate {
  date: string
  rate: number
  total: number
  success: number
}

/** 近 N 日趋势。 */
export interface DashboardTrends {
  tradeAmount: DailyAmount[]
  successRate: DailyRate[]
  refund: DailyAmount[]
}

/** 渠道占比项。 */
export interface ChannelItem {
  channelCode: string
  amount: number
}

/** 商户交易额 TOP 项。 */
export interface MerchantTopItem {
  merchantId: number
  merchantName: string
  amount: number
}

/** 今日 8 项核心指标。 */
export function getDashboardSummary() {
  return request.get<unknown, DashboardSummary>('/dashboard/summary')
}

/** 近 N 日趋势（1~90，默认 7），日期连续补零。 */
export function getDashboardTrends(days?: number) {
  return request.get<unknown, DashboardTrends>('/dashboard/trends', { params: { days } })
}

/** 渠道占比（成功单支付额）。 */
export function getChannelShare(days?: number) {
  return request.get<unknown, ChannelItem[]>('/dashboard/channel-share', { params: { days } })
}

/** 商户交易额 TOP10。 */
export function getMerchantTop(days?: number) {
  return request.get<unknown, MerchantTopItem[]>('/dashboard/merchant-top', { params: { days } })
}
