package org.example.finflowbackend.system.dto;

import java.util.List;

/**
 * Dashboard 近 N 日趋势（日期连续补零，date=yyyy-MM-dd）。
 */
public record DashboardTrendsResponse(
        List<DailyAmount> tradeAmount,
        List<DailyRate> successRate,
        List<DailyAmount> refund) {

    /** 按日金额点（交易额/退款共用）。 */
    public record DailyAmount(String date, long amount) {
    }

    /** 按日成功率点（rate 为百分比 0~100）。 */
    public record DailyRate(String date, double rate, long total, long success) {
    }
}
