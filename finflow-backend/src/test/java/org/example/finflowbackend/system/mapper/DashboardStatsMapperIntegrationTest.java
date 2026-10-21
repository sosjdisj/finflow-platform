package org.example.finflowbackend.system.mapper;

import org.example.finflowbackend.system.dto.DashboardChannelRow;
import org.example.finflowbackend.system.dto.DashboardMerchantRow;
import org.example.finflowbackend.system.dto.DashboardSummaryResponse;
import org.example.finflowbackend.system.dto.DashboardTrendsResponse;
import org.example.finflowbackend.system.dto.PaymentSummaryRow;
import org.example.finflowbackend.system.dto.TrendAmountRow;
import org.example.finflowbackend.system.dto.TrendCountRow;
import org.example.finflowbackend.system.service.DashboardStatsService;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Dashboard 统计 SQL 集成验收（真实本地 MySQL finflow_it，S39 换 Testcontainers）：
 * it 库含历史集成测试残留，所有聚合断言一律用「造数前基线 + 期望增量」的增量断言，不写绝对值。
 */
@Transactional
class DashboardStatsMapperIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DashboardStatsMapper statsMapper;

    @Autowired
    private DashboardStatsService statsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 100000);

    private long merchantA;
    private long merchantB;

    @BeforeEach
    void seedMerchants() {
        merchantA = insertMerchant("DASH验收商户A");
        merchantB = insertMerchant("DASH验收商户B");
    }

    @Test
    void summaryAggregatesAllSources() {
        LocalDateTime now = LocalDateTime.now();
        // 基线先行：it 库有历史残留，所有断言基于「基线 + 期望增量」
        PaymentSummaryRow baseline = statsMapper.selectPaymentTodaySummary();
        long baseFee = statsMapper.selectTodayPlatformFee();
        long baseRefund = statsMapper.selectTodayRefundAmount();
        long baseSettle = statsMapper.selectTodaySettlementAmount();
        long baseReject = statsMapper.selectTodayRiskRejectCount();

        // 今日 3 单：2 成功（1000 SETTLED + 2000 PAID）+ 1 失败
        insertPayment(merchantA, 1000L, "SETTLED", now);
        insertPayment(merchantA, 2000L, "PAID", now);
        insertPayment(merchantB, 5000L, "FAILED", now);
        // 昨日 1 单 CLOSED（不进今日口径）
        insertPayment(merchantA, 999L, "CLOSED", now.minusDays(1));

        // 今日退款成功 100 + 申请中 300（不计）
        insertRefund(merchantA, 100L, "SUCCESS", now);
        insertRefund(merchantA, 300L, "APPLYING", now);
        // 昨日退款成功 50（不进今日）
        insertRefund(merchantB, 50L, "SUCCESS", now.minusDays(1));

        // 今日结算成功 900 + 待执行 700（不计，另一商户防 uk(merchant_id,settle_date) 冲突）
        insertSettlement(merchantA, "SUCCESS", 900L, now);
        insertSettlement(merchantB, "CALCULATED", 700L, now);

        insertClearing(merchantA, 1000L, 10L, "MOCK_WALLET", LocalDate.now());
        insertClearing(merchantB, 2000L, 20L, "MOCK_BANK", LocalDate.now());
        insertClearing(merchantA, 8888L, 88L, "MOCK_WALLET", LocalDate.now().minusDays(1));

        insertRiskEvent(merchantA, "REJECT", now);
        insertRiskEvent(merchantB, "REJECT", now);
        insertRiskEvent(merchantA, "ALLOW", now);

        PaymentSummaryRow payment = statsMapper.selectPaymentTodaySummary();
        assertEquals(baseline.getTotalOrders() + 3, payment.getTotalOrders());
        assertEquals(baseline.getSuccessOrders() + 2, payment.getSuccessOrders());
        assertEquals(baseline.getSuccessAmount() + 3000L, payment.getSuccessAmount());
        assertEquals(baseline.getAbnormalOrders() + 1, payment.getAbnormalOrders());

        assertEquals(baseRefund + 100L, statsMapper.selectTodayRefundAmount());
        assertEquals(baseSettle + 900L, statsMapper.selectTodaySettlementAmount());
        assertEquals(baseFee + 30L, statsMapper.selectTodayPlatformFee());
        assertEquals(baseReject + 2L, statsMapper.selectTodayRiskRejectCount());
    }

    @Test
    void trendsShareAndTopAggregateByDate() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        // 基线：三日窗口内各口径的既有值
        Map<LocalDate, Long> baseTrade = statsMapper.selectTradeAmountTrend(3).stream()
                .collect(java.util.stream.Collectors.toMap(TrendAmountRow::getStatDate, TrendAmountRow::getAmount));
        Map<LocalDate, TrendCountRow> baseRate = statsMapper.selectSuccessRateTrend(3).stream()
                .collect(java.util.stream.Collectors.toMap(TrendCountRow::getStatDate, r -> r));
        Map<LocalDate, Long> baseRefund = statsMapper.selectRefundTrend(3).stream()
                .collect(java.util.stream.Collectors.toMap(TrendAmountRow::getStatDate, TrendAmountRow::getAmount));
        Map<String, Long> baseShare = statsMapper.selectChannelShare(3).stream()
                .collect(java.util.stream.Collectors.toMap(DashboardChannelRow::getChannelCode, DashboardChannelRow::getAmount));

        // 三日造单：前日 100 成功、昨日 0（测补零）、今日 300 成功 + 400 失败
        insertPayment(merchantA, 100L, "SETTLED", now.minusDays(2));
        insertPayment(merchantA, 300L, "PAID", now);
        insertPayment(merchantB, 400L, "FAILED", now);

        insertRefund(merchantA, 60L, "SUCCESS", now);
        insertRefund(merchantA, 40L, "SUCCESS", now.minusDays(2));

        insertClearing(merchantA, 1000L, 10L, "MOCK_WALLET", today);
        insertClearing(merchantB, 2000L, 20L, "MOCK_BANK", today);

        Map<LocalDate, Long> trade = statsMapper.selectTradeAmountTrend(3).stream()
                .collect(java.util.stream.Collectors.toMap(TrendAmountRow::getStatDate, TrendAmountRow::getAmount));
        assertEquals(baseTrade.getOrDefault(today.minusDays(2), 0L) + 100L, trade.get(today.minusDays(2)));
        assertEquals(baseTrade.getOrDefault(today, 0L) + 300L, trade.get(today));

        Map<LocalDate, TrendCountRow> rate = statsMapper.selectSuccessRateTrend(3).stream()
                .collect(java.util.stream.Collectors.toMap(TrendCountRow::getStatDate, r -> r));
        TrendCountRow todayRow = rate.get(today);
        TrendCountRow baseToday = baseRate.get(today);
        assertEquals((baseToday == null ? 0 : baseToday.getTotal()) + 2, todayRow.getTotal());
        assertEquals((baseToday == null ? 0 : baseToday.getSuccess()) + 1, todayRow.getSuccess());

        Map<LocalDate, Long> refund = statsMapper.selectRefundTrend(3).stream()
                .collect(java.util.stream.Collectors.toMap(TrendAmountRow::getStatDate, TrendAmountRow::getAmount));
        assertEquals(baseRefund.getOrDefault(today, 0L) + 60L, refund.get(today));
        assertEquals(baseRefund.getOrDefault(today.minusDays(2), 0L) + 40L, refund.get(today.minusDays(2)));

        Map<String, Long> share = statsMapper.selectChannelShare(3).stream()
                .collect(java.util.stream.Collectors.toMap(DashboardChannelRow::getChannelCode, DashboardChannelRow::getAmount));
        assertEquals(baseShare.getOrDefault("MOCK_WALLET", 0L) + 1000L, share.get("MOCK_WALLET"));
        assertEquals(baseShare.getOrDefault("MOCK_BANK", 0L) + 2000L, share.get("MOCK_BANK"));

        // Service 层补零：窗口固定 3 个连续日期
        DashboardTrendsResponse trends = statsService.trends(3);
        assertEquals(3, trends.tradeAmount().size());
        assertEquals(3, trends.successRate().size());
        assertEquals(today.toString(), trends.tradeAmount().get(2).date());

        // TOP10：it 库历史商户噪声大，只验证可移植性质——限量 10、金额非增序、JOIN 名称非空
        List<DashboardMerchantRow> top = statsMapper.selectMerchantTop(3);
        org.springframework.util.Assert.isTrue(top.size() <= 10, "TOP 最多 10 行");
        DashboardMerchantRow prev = null;
        for (DashboardMerchantRow row : top) {
            if (prev != null) {
                org.springframework.util.Assert.isTrue(prev.getAmount() >= row.getAmount(), "金额非增序");
            }
            org.springframework.util.Assert.isTrue(row.getMerchantName() != null && !row.getMerchantName().isBlank(),
                    "JOIN merchant 名称非空");
            prev = row;
        }
    }

    // ---------- 造数辅助（直插 SQL，字段最小集） ----------

    private long insertMerchant(String name) {
        String suffix = String.valueOf(SEQ.incrementAndGet());
        jdbcTemplate.update("""
                INSERT INTO merchant (merchant_no, merchant_name, contact_name, contact_phone,
                                      business_license_no, status, created_at, updated_at)
                VALUES (?, ?, '联系人', '13800000000', ?, 'ACTIVE', NOW(), NOW())
                """, "M" + suffix, name + suffix, "BL" + suffix);
        return jdbcTemplate.queryForObject("SELECT id FROM merchant WHERE merchant_no = ?", Long.class, "M" + suffix);
    }

    private void insertPayment(long merchantId, long amount, String status, LocalDateTime createdAt) {
        String suffix = String.valueOf(SEQ.incrementAndGet());
        jdbcTemplate.update("""
                INSERT INTO payment_order (merchant_id, merchant_order_no, platform_order_no, amount,
                                           currency, subject, payment_method, expired_at, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'CNY', 'Dashboard验收', 'ALIPAY', ?, ?, ?, NOW())
                """, merchantId, "DASH" + suffix, "PDASH" + suffix, amount,
                createdAt.plusHours(2), status, createdAt);
    }

    private void insertRefund(long merchantId, long amount, String status, LocalDateTime refundedAt) {
        String suffix = String.valueOf(SEQ.incrementAndGet());
        jdbcTemplate.update("""
                INSERT INTO refund_order (refund_no, order_id, merchant_id, platform_order_no, refund_amount,
                                          currency, status, operator, refunded_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'CNY', ?, 'DASH', ?, ?, NOW())
                """, "RDASH" + suffix, SEQ.incrementAndGet(), merchantId, "PDASH" + suffix,
                amount, status, status.equals("SUCCESS") ? refundedAt : null, refundedAt);
    }

    private void insertSettlement(long merchantId, String status, long totalAmount, LocalDateTime executedAt) {
        String suffix = String.valueOf(SEQ.incrementAndGet());
        jdbcTemplate.update("""
                INSERT INTO settlement_order (settle_no, merchant_id, settle_date, order_count, total_amount,
                                              currency, status, executed_at, generated_at)
                VALUES (?, ?, ?, 1, ?, 'CNY', ?, ?, NOW())
                """, "STDASH" + suffix, merchantId, executedAt.toLocalDate(), totalAmount,
                status, status.equals("SUCCESS") ? executedAt : null);
    }

    private void insertClearing(long merchantId, long payAmount, long platformFee, String channelCode, LocalDate settleDate) {
        String suffix = String.valueOf(SEQ.incrementAndGet());
        jdbcTemplate.update("""
                INSERT INTO clearing_order (order_id, merchant_id, platform_order_no, pay_amount, merchant_income,
                                            platform_fee, channel_cost, currency, channel_code, settle_date,
                                            status, cleared_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, 'CNY', ?, ?, 'CLEARED', NOW())
                """, SEQ.incrementAndGet(), merchantId, "PDASH" + suffix, payAmount,
                payAmount - platformFee, platformFee, channelCode, settleDate);
    }

    private void insertRiskEvent(long merchantId, String verdict, LocalDateTime createdAt) {
        String suffix = String.valueOf(SEQ.incrementAndGet());
        jdbcTemplate.update("""
                INSERT INTO risk_event (event_no, order_id, merchant_id, amount, hit_rules, total_score,
                                        verdict, created_at)
                VALUES (?, ?, ?, 100, NULL, 0, ?, ?)
                """, "REDASH" + suffix, SEQ.incrementAndGet(), merchantId, verdict, createdAt);
    }
}
