package org.example.finflowbackend.ledger.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.finflowbackend.account.AccountOwnerType;
import org.example.finflowbackend.account.entity.Account;
import org.example.finflowbackend.account.service.AccountService;
import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.ledger.entity.AccountLedger;
import org.example.finflowbackend.ledger.mapper.AccountLedgerMapper;
import org.example.finflowbackend.refund.event.RefundTopics;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 退款账务集成验收（依赖本地 MySQL/Redis，S39 换 Testcontainers）：
 * 消费 RefundSucceeded → 平台备付金出账 + 流水留痕；重复投递不重复出账。
 */
class RefundLedgerConsumerTest extends IntegrationTestBase {

    private static final String DEDUP_KEY_PREFIX = "event:consumed:finflow-ledger:";

    @Autowired
    private RefundLedgerConsumer consumer;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountLedgerMapper ledgerMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void consumeRefundSucceededDebitsPlatformAccount() {
        String refundNo = "RPLT" + Long.toHexString(System.nanoTime());
        Account platform = accountService.getOrCreate(AccountOwnerType.PLATFORM, 0L, "CNY");
        long beforeBalance = platform.getAvailableBalance();

        consumer.handle(message(UUID.randomUUID().toString(), refundNo, 3000L));

        assertEquals(beforeBalance - 3000L,
                accountService.getAccount(platform.getId()).getAvailableBalance());
        AccountLedger ledger = ledgerMapper.selectOne(new LambdaQueryWrapper<AccountLedger>()
                .eq(AccountLedger::getBizType, "REFUND")
                .eq(AccountLedger::getBizNo, refundNo));
        assertEquals(-3000L, ledger.getAmount());
        assertEquals(beforeBalance, ledger.getBalanceBefore());
        assertEquals(beforeBalance - 3000L, ledger.getBalanceAfter());
    }

    @Test
    void duplicateDeliveryDoesNotDoubleDebit() {
        String eventId = UUID.randomUUID().toString();
        String refundNo = "RPLT" + Long.toHexString(System.nanoTime());
        Account platform = accountService.getOrCreate(AccountOwnerType.PLATFORM, 0L, "CNY");
        long beforeBalance = platform.getAvailableBalance();

        consumer.handle(message(eventId, refundNo, 2000L));
        consumer.handle(message(eventId, refundNo, 2000L));
        assertEquals(1L, countLedger(refundNo));
        assertEquals(beforeBalance - 2000L,
                accountService.getAccount(platform.getId()).getAvailableBalance());

        // 绕过 Redis 去重窗口的重投：流水表 uk(biz_type, biz_no, account_id) 兜底
        redisTemplate.delete(DEDUP_KEY_PREFIX + eventId);
        consumer.handle(message(eventId, refundNo, 2000L));
        assertEquals(1L, countLedger(refundNo));
        assertEquals(beforeBalance - 2000L,
                accountService.getAccount(platform.getId()).getAvailableBalance());
    }

    private String message(String eventId, String refundNo, long amount) {
        DomainEvent event = new DomainEvent(eventId, RefundTopics.TYPE_REFUND_SUCCEEDED, Instant.now(),
                new org.example.finflowbackend.refund.event.RefundSucceededPayload(
                        1L, refundNo, "P20260101000099", 10L, amount, "CNY", LocalDateTime.now()));
        return objectMapper.writeValueAsString(event);
    }

    private long countLedger(String refundNo) {
        return ledgerMapper.selectCount(new LambdaQueryWrapper<AccountLedger>()
                .eq(AccountLedger::getBizType, "REFUND")
                .eq(AccountLedger::getBizNo, refundNo));
    }
}
