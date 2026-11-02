package org.example.finflowbackend.openapi.quota;

/**
 * 配额视图（后台管理接口返回）：qpsLimit / dailyLimit 为 0 表示沿用平台默认，
 * effective* 为实际生效值，管理员不必自己换算默认值。
 */
public record OpenApiQuotaView(String apiCode, Integer qpsLimit, Long dailyLimit, Boolean enabled,
                               int effectiveQpsLimit, long effectiveDailyLimit) {
}
