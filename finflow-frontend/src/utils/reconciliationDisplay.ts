/** 对账中心展示文案：任务状态 / 五类匹配结果 / 差异单状态与处理方式。 */

export const RECON_TASK_STATUS_TEXT: Record<string, string> = {
  PENDING: '待解析',
  PARSING: '解析中',
  COMPLETED: '解析完成',
  FAILED: '解析失败',
}

export const RECON_RESULT_TEXT: Record<string, string> = {
  MATCHED: '匹配一致',
  AMOUNT_MISMATCH: '金额不一致',
  STATUS_MISMATCH: '状态不一致',
  MISSING_IN_PLATFORM: '渠道有平台无',
  MISSING_IN_CHANNEL: '平台有渠道无',
}

export const RECON_DIFF_STATUS_TEXT: Record<string, string> = {
  PENDING: '待审核',
  CONFIRMED: '已确认',
  CLOSED: '已关闭',
  IGNORED: '已忽略',
}

export const RECON_HANDLE_TYPE_TEXT: Record<string, string> = {
  ADJUST: '调账',
  SUPPLEMENT: '补单',
}

export function reconTaskStatusText(status: string): string {
  return RECON_TASK_STATUS_TEXT[status] ?? status
}

export function reconResultText(result: string): string {
  return RECON_RESULT_TEXT[result] ?? result
}

export function reconDiffStatusText(status: string): string {
  return RECON_DIFF_STATUS_TEXT[status] ?? status
}

export function reconHandleTypeText(type: string): string {
  return RECON_HANDLE_TYPE_TEXT[type] ?? type
}

/** 匹配结果标签色（el-tag type）。 */
export function reconResultTag(result: string): 'success' | 'danger' | 'warning' | 'info' {
  switch (result) {
    case 'MATCHED':
      return 'success'
    case 'AMOUNT_MISMATCH':
    case 'MISSING_IN_PLATFORM':
      return 'danger'
    case 'STATUS_MISMATCH':
      return 'warning'
    default:
      return 'info'
  }
}

/** 差异单状态标签色。 */
export function reconDiffStatusTag(status: string): 'success' | 'warning' | 'info' | 'primary' {
  switch (status) {
    case 'CLOSED':
      return 'success'
    case 'CONFIRMED':
      return 'warning'
    case 'IGNORED':
      return 'info'
    default:
      return 'primary'
  }
}
