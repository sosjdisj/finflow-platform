/** 结算单状态展示文案。 */
export const SETTLEMENT_STATUS_TEXT: Record<string, string> = {
  PENDING: '生成中',
  CALCULATED: '待审核',
  APPROVED: '待执行',
  PROCESSING: '执行中',
  SUCCESS: '已入账',
  FAILED: '执行失败',
}

/** 结算状态标签类型（el-tag type）。 */
export const SETTLEMENT_STATUS_TAG: Record<string, string> = {
  PENDING: 'info',
  CALCULATED: 'warning',
  APPROVED: 'primary',
  PROCESSING: 'primary',
  SUCCESS: 'success',
  FAILED: 'danger',
}

export function settlementStatusText(status: string): string {
  return SETTLEMENT_STATUS_TEXT[status] ?? status
}

export function settlementStatusTag(status: string): string {
  return SETTLEMENT_STATUS_TAG[status] ?? 'info'
}

/** 是否可审核（仅待审核单）。 */
export function isApprovable(status: string): boolean {
  return status === 'CALCULATED'
}

/** 是否可执行（待执行单，或失败单重试）。 */
export function isExecutable(status: string): boolean {
  return status === 'APPROVED' || status === 'FAILED'
}
