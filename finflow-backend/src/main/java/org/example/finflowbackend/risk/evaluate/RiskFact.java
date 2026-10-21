package org.example.finflowbackend.risk.evaluate;

/**
 * 单次风控检查的评估事实（订单要素 + 风控相关环境要素）。
 */
public record RiskFact(Long orderId, Long merchantId, long amount, String clientIp, String deviceId) {
}
