package org.example.finflowbackend.system.service;

import org.example.finflowbackend.system.dto.DashboardChannelRow;
import org.example.finflowbackend.system.dto.DashboardMerchantRow;
import org.example.finflowbackend.system.dto.DashboardSummaryResponse;
import org.example.finflowbackend.system.dto.DashboardTrendsResponse;
import org.example.finflowbackend.system.dto.PaymentSummaryRow;
import org.example.finflowbackend.system.dto.TrendAmountRow;
import org.example.finflowbackend.system.dto.TrendCountRow;
import org.example.finflowbackend.system.mapper.DashboardStatsMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dashboard 统计服务单测：归一化 days、日期连续补零、成功率除零与计算。
 */
class DashboardStatsServiceTest {

    private final DashboardStatsMapper statsMapper = mock(DashboardStatsMapper.class);
    private final DashboardStatsService service = new DashboardStatsService(statsMapper);

    @Test
    void summaryMapsRowsAndComputesRate() {
        PaymentSummaryRow payment = new PaymentSummaryRow();
        payment.setTotalOrders(3);
        payment.setSuccessOrders(2);
        payment.setSuccessAmount(3000L);
        payment.setAbnormalOrders(1);
        when(statsMapper.selectPaymentTodaySummary()).thenReturn(payment);
        when(statsMapper.selectTodayRefundAmount()).thenReturn(100L);
        when(statsMapper.selectTodaySettlementAmount()).thenReturn(900L);
        when(statsMapper.selectTodayPlatformFee()).thenReturn(30L);
        when(statsMapper.selectTodayRiskRejectCount()).thenReturn(2L);

        DashboardSummaryResponse summary = service.summary();

        assertEquals(3000L, summary.todayTradeAmount());
        assertEquals(3, summary.todayOrderCount());
        assertEquals(66.67, summary.successRate());
        assertEquals(100L, summary.todayRefundAmount());
        assertEquals(900L, summary.todaySettlementAmount());
        assertEquals(30L, summary.todayPlatformFee());
        assertEquals(2L, summary.todayRiskRejectCount());
        assertEquals(1L, summary.todayAbnormalOrderCount());
    }

    @Test
    void summaryZeroOrdersYieldsZeroRate() {
        when(statsMapper.selectPaymentTodaySummary()).thenReturn(new PaymentSummaryRow());
        when(statsMapper.selectTodayRefundAmount()).thenReturn(0L);
        when(statsMapper.selectTodaySettlementAmount()).thenReturn(0L);
        when(statsMapper.selectTodayPlatformFee()).thenReturn(0L);
        when(statsMapper.selectTodayRiskRejectCount()).thenReturn(0L);

        assertEquals(0.0, service.summary().successRate());
    }

    @Test
    void trendsNormalizeDaysAndFillZeroDates() {
        // days=null → 默认 7；mapper 收到 7
        stubEmptyTrends();
        service.trends(null);
        verify(statsMapper).selectTradeAmountTrend(7);
        verify(statsMapper).selectSuccessRateTrend(7);
        verify(statsMapper).selectRefundTrend(7);

        // days=1000 → 截到 90
        stubEmptyTrends();
        DashboardTrendsResponse capped = service.trends(1000);
        verify(statsMapper).selectSuccessRateTrend(90);
        assertEquals(90, capped.tradeAmount().size());

        // days=3：中间缺日补零、成功率按日计算
        LocalDate today = LocalDate.now();
        TrendAmountRow yesterdayTrade = row(today.minusDays(1), 500L);
        when(statsMapper.selectTradeAmountTrend(3)).thenReturn(List.of(yesterdayTrade));
        TrendCountRow todayRate = countRow(today, 2, 1);
        when(statsMapper.selectSuccessRateTrend(3)).thenReturn(List.of(todayRate));
        when(statsMapper.selectRefundTrend(3)).thenReturn(List.of());

        DashboardTrendsResponse trends = service.trends(3);

        assertEquals(3, trends.tradeAmount().size());
        assertEquals(0L, trends.tradeAmount().get(0).amount());
        assertEquals(500L, trends.tradeAmount().get(1).amount());
        assertEquals(today.toString(), trends.tradeAmount().get(2).date());
        assertEquals(50.0, trends.successRate().get(2).rate());
        assertEquals(0.0, trends.successRate().get(0).rate());
        assertEquals(0L, trends.refund().get(2).amount());
    }

    @Test
    void channelShareAndMerchantTopMapItems() {
        DashboardChannelRow channel = new DashboardChannelRow();
        channel.setChannelCode("MOCK_WALLET");
        channel.setAmount(3000L);
        when(statsMapper.selectChannelShare(anyInt())).thenReturn(List.of(channel));
        DashboardMerchantRow merchant = new DashboardMerchantRow();
        merchant.setMerchantId(11L);
        merchant.setMerchantName("测试商户");
        merchant.setAmount(3000L);
        when(statsMapper.selectMerchantTop(anyInt())).thenReturn(List.of(merchant));

        assertEquals("MOCK_WALLET", service.channelShare(7).get(0).channelCode());
        assertEquals(3000L, service.channelShare(7).get(0).amount());
        assertEquals("测试商户", service.merchantTop(7).get(0).merchantName());
        assertEquals(11L, service.merchantTop(7).get(0).merchantId());
    }

    private void stubEmptyTrends() {
        when(statsMapper.selectTradeAmountTrend(anyInt())).thenReturn(List.of());
        when(statsMapper.selectSuccessRateTrend(anyInt())).thenReturn(List.of());
        when(statsMapper.selectRefundTrend(anyInt())).thenReturn(List.of());
    }

    private TrendAmountRow row(LocalDate date, long amount) {
        TrendAmountRow r = new TrendAmountRow();
        r.setStatDate(date);
        r.setAmount(amount);
        return r;
    }

    private TrendCountRow countRow(LocalDate date, long total, long success) {
        TrendCountRow r = new TrendCountRow();
        r.setStatDate(date);
        r.setTotal(total);
        r.setSuccess(success);
        return r;
    }
}
