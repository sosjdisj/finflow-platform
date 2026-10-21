package org.example.finflowbackend.system.service;

import org.example.finflowbackend.system.dto.DashboardChannelItem;
import org.example.finflowbackend.system.dto.DashboardChannelRow;
import org.example.finflowbackend.system.dto.DashboardMerchantItem;
import org.example.finflowbackend.system.dto.DashboardMerchantRow;
import org.example.finflowbackend.system.dto.DashboardSummaryResponse;
import org.example.finflowbackend.system.dto.DashboardTrendsResponse;
import org.example.finflowbackend.system.dto.PaymentSummaryRow;
import org.example.finflowbackend.system.dto.TrendAmountRow;
import org.example.finflowbackend.system.dto.TrendCountRow;
import org.example.finflowbackend.system.mapper.DashboardStatsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dashboard 统计服务（S36）：简单聚合查询，不做实时计算。
 * 趋势查询在 Java 层按日期连续补零，前端无需处理空洞。
 */
@Service
public class DashboardStatsService {

    static final int DEFAULT_DAYS = 7;
    static final int MAX_DAYS = 90;

    private final DashboardStatsMapper statsMapper;

    public DashboardStatsService(DashboardStatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        PaymentSummaryRow payment = statsMapper.selectPaymentTodaySummary();
        double successRate = payment.getTotalOrders() == 0 ? 0.0
                : BigDecimal.valueOf(payment.getSuccessOrders() * 100L)
                .divide(BigDecimal.valueOf(payment.getTotalOrders()), 2, RoundingMode.HALF_UP)
                .doubleValue();
        return new DashboardSummaryResponse(
                payment.getSuccessAmount(),
                payment.getTotalOrders(),
                successRate,
                statsMapper.selectTodayRefundAmount(),
                statsMapper.selectTodaySettlementAmount(),
                statsMapper.selectTodayPlatformFee(),
                statsMapper.selectTodayRiskRejectCount(),
                payment.getAbnormalOrders());
    }

    @Transactional(readOnly = true)
    public DashboardTrendsResponse trends(Integer days) {
        int realDays = normalizeDays(days);
        LocalDate start = LocalDate.now().minusDays(realDays - 1L);
        List<LocalDate> dateRange = start.datesUntil(start.plusDays(realDays)).toList();

        Map<LocalDate, TrendAmountRow> trade = byDate(statsMapper.selectTradeAmountTrend(realDays), TrendAmountRow::getStatDate);
        Map<LocalDate, TrendCountRow> rate = statsMapper.selectSuccessRateTrend(realDays).stream()
                .collect(Collectors.toMap(TrendCountRow::getStatDate, Function.identity()));
        Map<LocalDate, TrendAmountRow> refund = byDate(statsMapper.selectRefundTrend(realDays), TrendAmountRow::getStatDate);

        List<DashboardTrendsResponse.DailyAmount> tradePoints = dateRange.stream()
                .map(date -> new DashboardTrendsResponse.DailyAmount(date.toString(),
                        trade.containsKey(date) ? trade.get(date).getAmount() : 0L))
                .toList();
        List<DashboardTrendsResponse.DailyRate> ratePoints = dateRange.stream()
                .map(date -> {
                    TrendCountRow row = rate.get(date);
                    long total = row == null ? 0 : row.getTotal();
                    long success = row == null ? 0 : row.getSuccess();
                    double r = total == 0 ? 0.0
                            : BigDecimal.valueOf(success * 100L).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP).doubleValue();
                    return new DashboardTrendsResponse.DailyRate(date.toString(), r, total, success);
                })
                .toList();
        List<DashboardTrendsResponse.DailyAmount> refundPoints = dateRange.stream()
                .map(date -> new DashboardTrendsResponse.DailyAmount(date.toString(),
                        refund.containsKey(date) ? refund.get(date).getAmount() : 0L))
                .toList();
        return new DashboardTrendsResponse(tradePoints, ratePoints, refundPoints);
    }

    @Transactional(readOnly = true)
    public List<DashboardChannelItem> channelShare(Integer days) {
        return toItems(statsMapper.selectChannelShare(normalizeDays(days)), DashboardChannelRow::getChannelCode);
    }

    @Transactional(readOnly = true)
    public List<DashboardMerchantItem> merchantTop(Integer days) {
        return statsMapper.selectMerchantTop(normalizeDays(days)).stream()
                .map(row -> new DashboardMerchantItem(row.getMerchantId(), row.getMerchantName(), row.getAmount()))
                .toList();
    }

    private int normalizeDays(Integer days) {
        if (days == null || days < 1) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }

    private Map<LocalDate, TrendAmountRow> byDate(List<TrendAmountRow> rows, Function<TrendAmountRow, LocalDate> key) {
        return rows.stream().collect(Collectors.toMap(key, Function.identity()));
    }

    private List<DashboardChannelItem> toItems(List<DashboardChannelRow> rows,
                                               Function<DashboardChannelRow, String> code) {
        return rows.stream().map(row -> new DashboardChannelItem(code.apply(row), row.getAmount())).toList();
    }
}
