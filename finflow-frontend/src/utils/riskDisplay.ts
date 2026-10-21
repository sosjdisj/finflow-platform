/** 风控相关展示文案（规则类型 / 参数说明 / 判决）。 */

/** 规则类型中文文案。 */
export function riskRuleTypeText(type: string): string {
  const map: Record<string, string> = {
    AMOUNT_LIMIT: '单笔金额限额',
    FREQUENCY: '时间窗口频次',
    BLACKLIST: '黑名单',
    SAME_IP_MULTI_ACCOUNT: '同 IP 多账户',
  }
  return map[type] ?? type
}

/** 判决中文文案与标签色。 */
export function riskVerdictText(verdict: string): string {
  const map: Record<string, string> = {
    ALLOW: '放行',
    REVIEW: '待人工审核',
    REJECT: '拒绝',
  }
  return map[verdict] ?? verdict
}

export function riskVerdictTagType(verdict: string): 'success' | 'warning' | 'danger' {
  if (verdict === 'ALLOW') return 'success'
  if (verdict === 'REJECT') return 'danger'
  return 'warning'
}

/** 黑名单维度文案。 */
export function riskDimensionText(dimension: string): string {
  const map: Record<string, string> = {
    USER: '用户（商户号）',
    IP: 'IP 地址',
    DEVICE: '设备号',
  }
  return map[dimension] ?? dimension
}

/**
 * 规则参数 JSON → 人读说明（表格 tooltip 用）。参数结构由后端各求值器定义。
 */
export function riskRuleParamsText(ruleType: string, paramsJson: string): string {
  try {
    const p = JSON.parse(paramsJson) as Record<string, unknown>
    switch (ruleType) {
      case 'AMOUNT_LIMIT':
        return `单笔金额超过 ${p.amountLimit} 分命中`
      case 'FREQUENCY':
        return `${p.windowSeconds} 秒窗口内支付超过 ${p.maxCount} 笔命中`
      case 'BLACKLIST':
        return `${riskDimensionText(String(p.dimension))}命中名单即拒绝`
      case 'SAME_IP_MULTI_ACCOUNT':
        return `${p.windowSeconds} 秒窗口内同 IP 超过 ${p.maxAccounts} 个账户命中`
      default:
        return paramsJson
    }
  } catch {
    return paramsJson
  }
}
