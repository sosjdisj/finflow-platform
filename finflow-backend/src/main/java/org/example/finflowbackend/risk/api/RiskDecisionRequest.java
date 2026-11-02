package org.example.finflowbackend.risk.api;

/**
 * 风控决策请求：订单要素快照。
 * clientIp / deviceId 为可选要素（黑名单、同 IP 多账户等规则使用，缺失时相关规则跳过）。
 * platformOrderNo 为 M16 补齐：拦截事件需要平台单号，通知服务据此渲染而无需回查订单。
 */
public record RiskDecisionRequest(Long orderId, Long merchantId, long amount,
                                  String clientIp, String deviceId, String platformOrderNo) {
}
