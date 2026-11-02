package org.example.finflowbackend.contract;

/**
 * 风控拦截事件负载（主题 {@link EventTopics#RISK_INTERCEPTED}）：判决为 REVIEW/REJECT 时发布（ALLOW 不发）。
 * M16 起携带商户与平台单号，通知服务不再回查订单（字段只增不改）。
 *
 * @param orderId        支付订单 ID
 * @param merchantId     商户 ID
 * @param platformOrderNo 平台单号
 * @param verdict        判决 REVIEW / REJECT
 * @param totalScore     命中规则累计风险分
 * @param reason         拦截原因（阈值说明或命中规则明细）
 */
public record RiskInterceptedPayload(Long orderId, Long merchantId, String platformOrderNo, String verdict,
                                     int totalScore, String reason) {
}
