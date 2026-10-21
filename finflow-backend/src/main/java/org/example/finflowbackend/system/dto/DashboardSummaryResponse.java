package org.example.finflowbackend.system.dto;

/**
 * Dashboard 今日汇总（金额一律分，前端转元展示）。
 */
public record DashboardSummaryResponse(
        long todayTradeAmount,
        long todayOrderCount,
        /** 支付成功率（百分比 0~100，保留 2 位小数）。 */
        double successRate,
        long todayRefundAmount,
        long todaySettlementAmount,
        long todayPlatformFee,
        long todayRiskRejectCount,
        long todayAbnormalOrderCount) {
}
