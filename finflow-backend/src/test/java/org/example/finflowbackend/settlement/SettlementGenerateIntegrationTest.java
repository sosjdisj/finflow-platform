package org.example.finflowbackend.settlement;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.settlement.entity.SettlementDetail;
import org.example.finflowbackend.settlement.entity.SettlementOrder;
import org.example.finflowbackend.settlement.mapper.SettlementDetailMapper;
import org.example.finflowbackend.settlement.mapper.SettlementOrderMapper;
import org.example.finflowbackend.settlement.service.SettlementGenerateService;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * S24 结算生成集成验收（依赖本地 MySQL/Redis，S39 换 Testcontainers）：
 * 造两天清分数据，任务各跑两次只出两张正确结算单；锁被占时跳过。
 */
class SettlementGenerateIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SettlementGenerateService generateService;

    @Autowired
    private SettlementOrderMapper settlementOrderMapper;

    @Autowired
    private SettlementDetailMapper settlementDetailMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Long merchantId;
    private LocalDate day1;
    private LocalDate day2;
    private String suffix;
    private long orderSeq;

    @BeforeEach
    void setUp() {
        suffix = Long.toHexString(System.nanoTime());
        orderSeq = 900_000_000_000L + Long.parseLong(suffix.substring(0, 8), 16);
        String name = "结算验收商户-" + suffix;
        jdbcTemplate.update("""
                        INSERT INTO merchant (merchant_no, merchant_name, contact_name, contact_phone,
                                              business_license_no, settle_cycle, status)
                        VALUES (?, ?, '测试联系人', '13800000000', ?, 'T1', 'ACTIVE')
                        """,
                "MCH" + suffix, name, "91110000MA01ST" + suffix.substring(0, 5).toUpperCase());
        merchantId = jdbcTemplate.queryForObject(
                "SELECT id FROM merchant WHERE merchant_name = ?", Long.class, name);
        // 验收口径「两天」：每个用例取唯一随机未来日期，隔离库中历史运行留下的清分/结算数据
        long offset = 1 + Long.parseLong(suffix.substring(0, 6), 16) % 3000;
        day1 = LocalDate.now().plusDays(offset);
        day2 = day1.plusDays(1);
        // 结算生成按 settle_date 全局扫描，先清掉这两个日期的历史残留（撞日期时会导致幂等断言失败）
        jdbcTemplate.update("""
                DELETE FROM settlement_detail WHERE settlement_id IN (
                    SELECT id FROM settlement_order WHERE settle_date IN (?, ?))
                """, day1, day2);
        jdbcTemplate.update("DELETE FROM settlement_order WHERE settle_date IN (?, ?)", day1, day2);
        jdbcTemplate.update("DELETE FROM clearing_order WHERE settle_date IN (?, ?)", day1, day2);
        // day1 两笔清分：9700 + 4850 = 14550；day2 一笔：9700
        insertClearing(1L, 10000L, 9700L, day1);
        insertClearing(2L, 5000L, 4850L, day1);
        insertClearing(3L, 10000L, 9700L, day2);
    }

    @Test
    void generatesOneSettlementPerDayAndIsIdempotentOnRerun() {
        // 任务跑两次（day1）+ 另一日一次：第二次幂等新生成 0，day1 恰一张
        assertEquals(1, generateService.generateFor(day1));
        assertEquals(0, generateService.generateFor(day1));
        assertEquals(1, generateService.generateFor(day2));

        SettlementOrder day1Order = settlementOrderMapper.selectOne(
                Wrappers.<SettlementOrder>query().eq("merchant_id", merchantId).eq("settle_date", day1));
        assertNotNull(day1Order);
        assertEquals(2, day1Order.getOrderCount());
        assertEquals(14550L, day1Order.getTotalAmount());
        assertEquals("CALCULATED", day1Order.getStatus());
        assertNotNull(day1Order.getCalculatedAt());
        assertNotNull(day1Order.getSettleNo());

        SettlementOrder day2Order = settlementOrderMapper.selectOne(
                Wrappers.<SettlementOrder>query().eq("merchant_id", merchantId).eq("settle_date", day2));
        assertNotNull(day2Order);
        assertEquals(1, day2Order.getOrderCount());
        assertEquals(9700L, day2Order.getTotalAmount());

        // 明细逐笔对应清分单，之和等于结算单总额
        Long day1DetailSum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(merchant_income),0) FROM settlement_detail WHERE settlement_id = ?",
                Long.class, day1Order.getId());
        assertEquals(14550L, day1DetailSum);
        Long day1DetailCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlement_detail WHERE settlement_id = ?",
                Long.class, day1Order.getId());
        assertEquals(2L, day1DetailCount);

        // 全库只有这两张该商户结算单（重复跑未多生成）
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlement_order WHERE merchant_id = ?", Long.class, merchantId);
        assertEquals(2L, total);
    }

    @Test
    void skipsWhenLockHeldByOtherInstance() {
        String lockKey = "finflow:settle:generate:" + day1;
        redisTemplate.opsForValue().set(lockKey, "other-instance", Duration.ofMinutes(1));
        try {
            assertEquals(0, generateService.generateFor(day1));
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM settlement_order WHERE merchant_id = ?", Long.class, merchantId);
            assertEquals(0L, count);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private void insertClearing(long seq, long payAmount, long merchantIncome, LocalDate settleDate) {
        // order_id 用随机段避开 uk(order_id)；清分单已处 CLEARED 终态
        jdbcTemplate.update("""
                        INSERT INTO clearing_order (order_id, merchant_id, platform_order_no, pay_amount,
                                                    merchant_income, platform_fee, channel_cost, currency,
                                                    channel_code, settle_date, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'CNY', 'MOCK_WALLET', ?, 'CLEARED')
                        """,
                ++orderSeq, merchantId, "PST" + suffix + seq, payAmount, merchantIncome,
                payAmount - merchantIncome - 100, 100, settleDate);
    }
}
