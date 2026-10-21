/** Webhook 事件状态展示文案。 */
export const WEBHOOK_STATUS_TEXT: Record<string, string> = {
  PENDING: '待投递/重试中',
  SUCCESS: '投递成功',
  EXHAUSTED: '重试耗尽',
}

/** Webhook 状态标签类型（el-tag type）。 */
export const WEBHOOK_STATUS_TAG: Record<string, string> = {
  PENDING: 'warning',
  SUCCESS: 'success',
  EXHAUSTED: 'danger',
}

/** Webhook 事件类型文案。 */
export const WEBHOOK_EVENT_TYPE_TEXT: Record<string, string> = {
  PAYMENT_CREATED: '支付创建',
  PAYMENT_SUCCEEDED: '支付成功',
  PAYMENT_FAILED: '支付失败',
  REFUND_SUCCEEDED: '退款成功',
}

export function webhookStatusText(status: string): string {
  return WEBHOOK_STATUS_TEXT[status] ?? status
}

export function webhookStatusTag(status: string): string {
  return WEBHOOK_STATUS_TAG[status] ?? 'info'
}

export function webhookEventTypeText(eventType: string): string {
  return WEBHOOK_EVENT_TYPE_TEXT[eventType] ?? eventType
}

/** 可手动重试（投递成功的事件后端拒绝重试，防商户重复处理）。 */
export function isWebhookRetryable(status: string): boolean {
  return status !== 'SUCCESS'
}

/** 通知模板编码文案。 */
export const NOTIFICATION_TEMPLATE_TEXT: Record<string, string> = {
  PAYMENT_SUCCEEDED: '支付成功',
  REFUND_SUCCEEDED: '退款成功',
  SETTLEMENT_SUCCEEDED: '结算完成',
  RISK_INTERCEPTED: '风控拦截',
  RECONCILIATION_DIFFERENCE: '对账异常',
}

/** 邮件渠道状态文案。 */
export const EMAIL_STATUS_TEXT: Record<string, string> = {
  SENT: '已发送',
  SKIPPED: '未发送（未配置）',
  FAILED: '发送失败',
}

/** 邮件状态标签类型（el-tag type）。 */
export const EMAIL_STATUS_TAG: Record<string, string> = {
  SENT: 'success',
  SKIPPED: 'info',
  FAILED: 'danger',
}

export function notificationTemplateText(templateCode: string): string {
  return NOTIFICATION_TEMPLATE_TEXT[templateCode] ?? templateCode
}

export function emailStatusText(emailStatus: string | null): string {
  return emailStatus ? (EMAIL_STATUS_TEXT[emailStatus] ?? emailStatus) : '无'
}

export function emailStatusTag(emailStatus: string | null): string {
  return emailStatus ? (EMAIL_STATUS_TAG[emailStatus] ?? 'info') : 'info'
}
