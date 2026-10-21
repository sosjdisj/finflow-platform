package org.example.finflowbackend.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.finflowbackend.system.dto.DashboardChannelRow;
import org.example.finflowbackend.system.dto.DashboardMerchantRow;
import org.example.finflowbackend.system.dto.PaymentSummaryRow;
import org.example.finflowbackend.system.dto.TrendAmountRow;
import org.example.finflowbackend.system.dto.TrendCountRow;

import java.util.List;

/**
 * Dashboard 统计 Mapper（S36）：手写 XML 聚合，跨 payment/refund/clearing/settlement/risk
 * 五张业务表只读查询 —— 报表场景特例，不写任何数据。
 */
@Mapper
public interface DashboardStatsMapper {

    PaymentSummaryRow selectPaymentTodaySummary();

    long selectTodayRefundAmount();

    long selectTodaySettlementAmount();

    long selectTodayPlatformFee();

    long selectTodayRiskRejectCount();

    /** 近 days 天交易额趋势（当日创建且终态成功，与 summary 口径一致）。 */
    List<TrendAmountRow> selectTradeAmountTrend(@Param("days") int days);

    /** 近 days 天成功率趋势（当日创建总数与终态成功数）。 */
    List<TrendCountRow> selectSuccessRateTrend(@Param("days") int days);

    /** 近 days 天退款趋势（退款成功时间口径）。 */
    List<TrendAmountRow> selectRefundTrend(@Param("days") int days);

    /** 近 days 天渠道占比（清分单按渠道聚合，只含成功单）。 */
    List<DashboardChannelRow> selectChannelShare(@Param("days") int days);

    /** 近 days 天商户交易额 TOP10（清分单口径，JOIN merchant 取名称）。 */
    List<DashboardMerchantRow> selectMerchantTop(@Param("days") int days);
}
