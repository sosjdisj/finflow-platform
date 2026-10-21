package org.example.finflowbackend.reconciliation.event;

import java.time.LocalDate;

/**
 * 对账异常事件负载：匹配完成后按差异类型汇总发布（每类一条，无差异不发）。
 * 金额为该类差异的平台侧 / 渠道侧合计（分），与差异单的逐笔明细互补——通知只做提醒，处理仍走差异单流程。
 *
 * @param taskId         对账任务 ID
 * @param taskNo         对账任务号
 * @param billDate       账单日期
 * @param channelCode    渠道编码
 * @param differenceType 差异类型（AMOUNT_MISMATCH / STATUS_MISMATCH / MISSING_IN_PLATFORM / MISSING_IN_CHANNEL）
 * @param platformAmount 平台侧金额合计（分；平台缺单时为 0）
 * @param channelAmount  渠道侧金额合计（分；渠道缺单时为 0）
 * @param count          该类差异笔数
 */
public record ReconciliationAbnormalPayload(Long taskId, String taskNo, LocalDate billDate,
                                            String channelCode, String differenceType,
                                            Long platformAmount, Long channelAmount, long count) {
}
