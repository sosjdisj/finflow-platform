package org.example.finflowbackend.ledger.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.finflowbackend.account.AccountOwnerType;
import org.example.finflowbackend.account.entity.Account;
import org.example.finflowbackend.account.service.AccountService;
import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.ledger.entity.AccountLedger;
import org.example.finflowbackend.ledger.mapper.AccountLedgerMapper;
import org.example.finflowbackend.ledger.service.LedgerService;
import org.example.finflowbackend.payment.event.PaymentSucceededPayload;
import org.example.finflowbackend.payment.event.PaymentTopics;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S19 账务入账集成验收（依赖本地 MySQL/Redis，S39 换 Testcontainers）：
 * 消费 PaymentSucceeded → 备付金入账 + 流水留痕；重复投递不重复入账（Redis 去重 + uk 兜底）；
 * 一致性校验能对平、也能发现账实不符。
 */
class PaymentLedgerIntegrationTest extends IntegrationTestBase {

    private static final String DEDUP_KEY_PREFIX = "event:consumed:finflow-ledger:";

    @Autowired
    private PaymentLedgerConsumer consumer;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountLedgerMapper ledgerMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void consumePaymentSucceededCreditsPlatformAccount() {
        String orderNo = "PLT" + Long.toHexString(System.nanoTime());
        long amount = 777L;
        Account platform = accountService.getOrCreate(AccountOwnerType.PLATFORM, 0L, "CNY");
        long beforeBalance = platform.getAvailableBalance();

        consumer.handle(message(UUID.randomUUID().toString(), orderNo, amount, "CNY"));

        assertEquals(beforeBalance + amount,
                accountService.getAccount(platform.getId()).getAvailableBalance());
        AccountLedger ledger = ledgerMapper.selectOne(new LambdaQueryWrapper<AccountLedger>()
                .eq(AccountLedger::getBizType, "PAYMENT")
                .eq(AccountLedger::getBizNo, orderNo));
        assertEquals(beforeBalance, ledger.getBalanceBefore());
        assertEquals(beforeBalance + amount, ledger.getBalanceAfter());
        assertEquals(amount, ledger.getAmount());
        assertEquals("CNY", ledger.getCurrency());
    }

    @Test
    void duplicateDeliveryDoesNotDoubleCredit() {
        String eventId = UUID.randomUUID().toString();
        String orderNo = "PLT" + Long.toHexString(System.nanoTime());
        Account platform = accountService.getOrCreate(AccountOwnerType.PLATFORM, 0L, "CNY");
        long beforeBalance = platform.getAvailableBalance();

        // 同一事件投递两次：第二次被 Redis 去重跳过
        consumer.handle(message(eventId, orderNo, 500L, "CNY"));
        consumer.handle(message(eventId, orderNo, 500L, "CNY"));
        assertEquals(1L, countLedger(orderNo));
        assertEquals(beforeBalance + 500L,
                accountService.getAccount(platform.getId()).getAvailableBalance());

        // 绕过 Redis 去重窗口的重投（删去重键后再投）：流水表 uk 兜底，仍不重复入账
        redisTemplate.delete(DEDUP_KEY_PREFIX + eventId);
        consumer.handle(message(eventId, orderNo, 500L, "CNY"));
        assertEquals(1L, countLedger(orderNo));
        assertEquals(beforeBalance + 500L,
                accountService.getAccount(platform.getId()).getAvailableBalance());
    }

    @Test
    void consistencyCheckDetectsBalanceMismatch() {
        Long ownerId = System.nanoTime();
        Account account = accountService.getOrCreate(AccountOwnerType.MERCHANT, ownerId, "CNY");
        ledgerService.credit(account.getId(), "PAYMENT", "CK-A-" + ownerId, 300L, "TEST");
        ledgerService.credit(account.getId(), "PAYMENT", "CK-B-" + ownerId, 200L, "TEST");

        var result = ledgerService.consistencyCheck(account.getId());
        assertEquals(500L, result.currentBalance());
        assertEquals(500L, result.ledgerSum());
        assertTrue(result.consistent());

        // 人为改坏余额，校验必须能发现账实不符
        jdbcTemplate.update(
                "UPDATE account SET available_balance = available_balance + 1 WHERE id = ?",
                account.getId());
        var broken = ledgerService.consistencyCheck(account.getId());
        assertEquals(501L, broken.currentBalance());
        assertFalse(broken.consistent());
    }

    private String message(String eventId, String orderNo, long amount, String currency) {
        DomainEvent event = new DomainEvent(eventId, PaymentTopics.TYPE_SUCCEEDED, Instant.now(),
                new PaymentSucceededPayload(1L, orderNo, amount, currency, LocalDateTime.now()));
        return objectMapper.writeValueAsString(event);
    }

    private long countLedger(String orderNo) {
        return ledgerMapper.selectCount(new LambdaQueryWrapper<AccountLedger>()
                .eq(AccountLedger::getBizType, "PAYMENT")
                .eq(AccountLedger::getBizNo, orderNo));
    }
}
