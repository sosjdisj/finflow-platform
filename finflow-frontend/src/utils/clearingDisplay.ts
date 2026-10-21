import type { ClearingDetail } from '../types/api'

/** 清分单状态展示文案（当前仅 CLEARED 单态）。 */
export const CLEARING_STATUS_TEXT: Record<string, string> = {
  CLEARED: '已清分',
}

/** 分账方向展示文案。 */
export const PARTY_TYPE_TEXT: Record<string, string> = {
  MERCHANT: '商户应得',
  PLATFORM: '平台手续费',
  CHANNEL: '渠道成本',
}

export function clearingStatusText(status: string): string {
  return CLEARING_STATUS_TEXT[status] ?? status
}

export function partyTypeText(partyType: string): string {
  return PARTY_TYPE_TEXT[partyType] ?? partyType
}

/** 分账方向标签色。 */
export function partyTypeTag(partyType: string): 'success' | 'warning' | 'info' {
  switch (partyType) {
    case 'MERCHANT':
      return 'success'
    case 'PLATFORM':
      return 'warning'
    default:
      return 'info'
  }
}

/** 明细行主语（商户/平台/渠道）。 */
export function partyLabel(detail: ClearingDetail): string {
  if (detail.partyType === 'MERCHANT') {
    return `商户 ${detail.partyId ?? '-'}`
  }
  if (detail.partyType === 'PLATFORM') {
    return '平台备付金'
  }
  return `渠道 ${detail.channelCode ?? '-'}`
}
