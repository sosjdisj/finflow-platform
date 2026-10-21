package org.example.finflowbackend.risk.event;

/**
 * 风控拦截事件负载：判决为 REVIEW/REJECT 时发布（ALLOW 不发）。
 * 平台单号与商户归属由消费端回查订单取得（沿通知消费者「回查为准」惯例）。
 *
 * @param orderId    支付订单 ID
 * @param merchantId 商户 ID（冗余，消费端仍以回查订单为准）
 * @param verdict    判决 REVIEW / REJECT
 * @param totalScore 命中规则累计风险分
 * @param reason     拦截原因（阈值说明或命中规则明细）
 */
public record RiskInterceptedPayload(Long orderId, Long merchantId, String verdict,
                                     int totalScore, String reason) {
}
