package org.example.finflowbackend.settlement;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.example.finflowbackend.account.AccountErrorCode;
import org.example.finflowbackend.account.AccountOwnerType;
import org.example.finflowbackend.account.entity.Account;
import org.example.finflowbackend.account.service.AccountService;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.ledger.service.LedgerService;
import org.example.finflowbackend.settlement.entity.SettlementOrder;
import org.example.finflowbackend.settlement.event.SettlementTopics;
import org.example.finflowbackend.settlement.mapper.SettlementOrderMapper;
import org.example.finflowbackend.settlement.service.SettlementExecuteService;
import org.example.finflowbackend.settlement.service.SettlementGenerateService;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S25 结算审核与执行集成验收（依赖本地 MySQL/Redis/Kafka，S39 换 Testcontainers）：
 * 清分 → 结算单 → 审核 → 执行入账，流水能对平；失败标 FAILED 可重试；重复执行被状态机拒绝。
 */
class SettlementExecuteIntegrationTest extends IntegrationTestBase {

    private static final long SETTLE_AMOUNT = 9700L;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SettlementGenerateService generateService;
    @Autowired
    private SettlementOrderMapper settlementOrderMapper;
    @Autowired
    private SettlementExecuteService executeService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private LedgerService ledgerService;

    private Long merchantId;
    private LocalDate day1;
    private String suffix;
    private long orderSeq;

    @BeforeEach
    void setUp() {
        suffix = Long.toHexString(System.nanoTime());
        orderSeq = 800_000_000_000L + Long.parseLong(suffix.substring(0, 8), 16);
        String name = "结算执行验收商户-" + suffix;
        jdbcTemplate.update("""
                        INSERT INTO merchant (merchant_no, merchant_name, contact_name, contact_phone,
                                              business_license_no, settle_cycle, status)
                        VALUES (?, ?, '测试联系人', '13800000000', ?, 'T1', 'ACTIVE')
                        """,
                "MCH" + suffix, name, "91110000MA01EX" + suffix.substring(0, 5).toUpperCase());
        merchantId = jdbcTemplate.queryForObject(
                "SELECT id FROM merchant WHERE merchant_name = ?", Long.class, name);
        // 唯一随机未来日期：隔离库中历史运行留下的清分/结算数据（S24 踩坑）
        long offset = 1 + Long.parseLong(suffix.substring(0, 6), 16) % 300;
        day1 = LocalDate.now().plusDays(offset);
        // 随机窗口跨运行可能撞日期：残留的其他商户 CLEARED 记录会被一起生成结算单（expected 1 was 2 踩坑），
        // 未来日期的清分/结算数据必为测试残留，先物理清理保证用例自隔离
        jdbcTemplate.update("DELETE FROM clearing_order WHERE settle_date = ?", day1);
        jdbcTemplate.update("DELETE FROM settlement_order WHERE settle_date = ?", day1);
        insertClearing(SETTLE_AMOUNT);
    }

    @Test
    void approveAndExecuteMovesMoneyAndLedgerBalances() throws Exception {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps())) {
            consumer.subscribe(List.of(SettlementTopics.TOPIC_SETTLEMENT_SUCCEEDED));
            // 订阅就位后再触发链路，避免漏收
            Thread.sleep(1000);

            SettlementOrder order = generateApprovedOrder("it-admin");
            // 平台备付金是共享账户（历史运行/其他用例会抽干余额），先垫足再断言相对值
            Account platform = platformAccount();
            adjust(platform.getId(), 1_000_000L);
            long platformBefore = accountService.getAccount(platform.getId()).getAvailableBalance();

            executeService.execute(order.getId());

            SettlementOrder after = settlementOrderMapper.selectById(order.getId());
            assertEquals("SUCCESS", after.getStatus());
            assertNotNull(after.getExecutedAt());

            // 商户账户（新 suffix 商户必为新户）：0 → 应付额
            Account merchant = accountService.getOrCreate(AccountOwnerType.MERCHANT, merchantId, "CNY");
            assertEquals(SETTLE_AMOUNT, merchant.getAvailableBalance());
            // 平台备付金：出账应付额
            assertEquals(platformBefore - SETTLE_AMOUNT,
                    accountService.getAccount(platform.getId()).getAvailableBalance());

            // 两笔流水（bizType=SETTLEMENT, bizNo=settleNo）：平台出、商户入
            Long ledgerCount = jdbcTemplate.queryForObject("""
                            SELECT COUNT(*) FROM account_ledger WHERE biz_type = 'SETTLEMENT' AND biz_no = ?
                            """, Long.class, order.getSettleNo());
            assertEquals(2L, ledgerCount);
            assertEquals(-SETTLE_AMOUNT, jdbcTemplate.queryForObject("""
                            SELECT amount FROM account_ledger WHERE biz_type = 'SETTLEMENT' AND biz_no = ? AND account_id = ?
                            """, Long.class, order.getSettleNo(), platform.getId()));
            assertEquals(SETTLE_AMOUNT, jdbcTemplate.queryForObject("""
                            SELECT amount FROM account_ledger WHERE biz_type = 'SETTLEMENT' AND biz_no = ? AND account_id = ?
                            """, Long.class, order.getSettleNo(), merchant.getId()));

            // 流水能对平：两账户 余额 = Σ流水
            assertTrue(ledgerService.consistencyCheck(platform.getId()).consistent());
            assertTrue(ledgerService.consistencyCheck(merchant.getId()).consistent());

            // 留痕链完整：CALCULATED→APPROVED→PROCESSING→SUCCESS
            List<String> transitions = jdbcTemplate.queryForList("""
                            SELECT CONCAT(from_status, '->', to_status) FROM settlement_order_state_log
                            WHERE settlement_id = ? ORDER BY id
                            """, String.class, order.getId());
            assertEquals(List.of("CALCULATED->APPROVED", "APPROVED->PROCESSING", "PROCESSING->SUCCESS"),
                    transitions);

            // 结算完成事件已发布 Kafka（payload 含本单单号）
            boolean received = pollAll(consumer, 15).stream()
                    .anyMatch(r -> r.topic().equals(SettlementTopics.TOPIC_SETTLEMENT_SUCCEEDED)
                            && r.value().contains(order.getSettleNo()));
            assertTrue(received, "未收到本结算单的 SETTLEMENT_SUCCEEDED 事件");
        }
    }

    @Test
    void executeRejectsBeforeApproval() {
        SettlementOrder order = generateOrder();
        BusinessException error = assertThrows(BusinessException.class,
                () -> executeService.execute(order.getId()));
        assertEquals(SettlementErrorCode.ILLEGAL_STATUS_TRANSITION, error.getErrorCode());
        assertEquals("CALCULATED", settlementOrderMapper.selectById(order.getId()).getStatus());
    }

    @Test
    void insufficientBalanceMarksFailedAndRetrySucceeds() {
        // 构造备付金不足：先垫大额再调减到低于应付额（平台户是共享账户，余额不可预设）
        Account platform = platformAccount();
        adjust(platform.getId(), 5_000_000L);
        long current = accountService.getAccount(platform.getId()).getAvailableBalance();
        adjust(platform.getId(), -(current - SETTLE_AMOUNT + 1));
        long beforeFailed = accountService.getAccount(platform.getId()).getAvailableBalance();

        SettlementOrder order = generateApprovedOrder("it-admin");
        BusinessException error = assertThrows(BusinessException.class,
                () -> executeService.execute(order.getId()));
        // account 模块的余额不足错误原样抛出（统一错误结构）
        assertEquals(AccountErrorCode.ACCOUNT_INSUFFICIENT_BALANCE, error.getErrorCode());

        SettlementOrder failed = settlementOrderMapper.selectById(order.getId());
        assertEquals("FAILED", failed.getStatus());
        assertNotNull(failed.getFailReason());
        // 事务回滚：钱没动、无结算流水、商户未建户
        assertEquals(beforeFailed, accountService.getAccount(platform.getId()).getAvailableBalance());
        Long ledgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_ledger WHERE biz_type = 'SETTLEMENT' AND biz_no = ?",
                Long.class, order.getSettleNo());
        assertEquals(0L, ledgerCount);
        Long merchantAccounts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account WHERE owner_type = 'MERCHANT' AND owner_id = ?",
                Long.class, merchantId);
        assertEquals(0L, merchantAccounts);

        // 补足备付金后重试：FAILED → PROCESSING → SUCCESS
        adjust(platform.getId(), SETTLE_AMOUNT * 2);
        executeService.execute(order.getId());
        SettlementOrder retried = settlementOrderMapper.selectById(order.getId());
        assertEquals("SUCCESS", retried.getStatus());

        Account merchant = accountService.getOrCreate(AccountOwnerType.MERCHANT, merchantId, "CNY");
        assertEquals(SETTLE_AMOUNT, merchant.getAvailableBalance());
        assertTrue(ledgerService.consistencyCheck(platform.getId()).consistent());
        assertTrue(ledgerService.consistencyCheck(merchant.getId()).consistent());
        // 重试只动了一次账
        Long retriedLedgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_ledger WHERE biz_type = 'SETTLEMENT' AND biz_no = ?",
                Long.class, order.getSettleNo());
        assertEquals(2L, retriedLedgerCount);
    }

    @Test
    void repeatedExecuteIsRejectedAfterSuccess() {
        // 平台备付金共享账户：先垫足，保证本用例不受其他用例/历史运行影响
        Account platform = platformAccount();
        adjust(platform.getId(), 1_000_000L);
        SettlementOrder order = generateApprovedOrder("it-admin");
        executeService.execute(order.getId());
        BusinessException error = assertThrows(BusinessException.class,
                () -> executeService.execute(order.getId()));
        assertEquals(SettlementErrorCode.ILLEGAL_STATUS_TRANSITION, error.getErrorCode());
    }

    // ---- 辅助 ----

    private SettlementOrder generateOrder() {
        assertEquals(1, generateService.generateFor(day1));
        return settlementOrderMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SettlementOrder>query()
                        .eq("merchant_id", merchantId).eq("settle_date", day1));
    }

    private SettlementOrder generateApprovedOrder(String operator) {
        SettlementOrder order = generateOrder();
        executeService.approve(order.getId(), operator);
        SettlementOrder approved = settlementOrderMapper.selectById(order.getId());
        assertEquals("APPROVED", approved.getStatus());
        assertEquals(operator, approved.getApprovedBy());
        return order;
    }

    private Account platformAccount() {
        return accountService.getOrCreate(AccountOwnerType.PLATFORM, 0L, "CNY");
    }

    private void adjust(Long accountId, long amount) {
        ledgerService.adjust(accountId, amount, "S25 集成测试备付金调整");
    }

    private void insertClearing(long merchantIncome) {
        jdbcTemplate.update("""
                        INSERT INTO clearing_order (order_id, merchant_id, platform_order_no, pay_amount,
                                                    merchant_income, platform_fee, channel_cost, currency,
                                                    channel_code, settle_date, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'CNY', 'MOCK_WALLET', ?, 'CLEARED')
                        """,
                ++orderSeq, merchantId, "PST" + suffix + orderSeq, 10000L, merchantIncome, 200L, 100L, day1);
    }

    private Properties consumerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-settle-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return props;
    }

    /** 轮询收集记录：数据静默 2 秒即返回（最长 15 秒）。 */
    private List<ConsumerRecord<String, String>> pollAll(KafkaConsumer<String, String> consumer,
                                                         int timeoutSeconds)
            throws InterruptedException {
        List<ConsumerRecord<String, String>> all = new java.util.ArrayList<>();
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        long quietDeadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline && System.currentTimeMillis() < quietDeadline) {
            int before = all.size();
            consumer.poll(java.time.Duration.ofMillis(500)).forEach(all::add);
            if (all.size() > before) {
                quietDeadline = System.currentTimeMillis() + 2000;
            }
        }
        return all;
    }
}
