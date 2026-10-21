package org.example.finflowbackend.account;

import org.example.finflowbackend.account.entity.Account;
import org.example.finflowbackend.account.mapper.AccountMapper;
import org.example.finflowbackend.account.service.AccountService;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S18 账户并发验收（依赖本地 MySQL，S39 换 Testcontainers）：
 * 并发扣款不超扣、余额最终正确；冻结/解冻余额守恒；金额与余额校验生效。
 */
class AccountConcurrencyIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountMapper accountMapper;

    private Long accountId;

    @BeforeEach
    void setUp() {
        // 账户表无外键，owner 用随机数避免多次运行冲突
        Long ownerId = System.nanoTime();
        accountId = accountService.getOrCreate(AccountOwnerType.MERCHANT, ownerId, "CNY").getId();
    }

    @Test
    void getOrCreateIsIdempotent() {
        AccountOwnerType ownerType = AccountOwnerType.MERCHANT;
        Long ownerId = System.nanoTime();
        Account first = accountService.getOrCreate(ownerType, ownerId, "CNY");
        Account second = accountService.getOrCreate(ownerType, ownerId, "CNY");
        assertEquals(first.getId(), second.getId());
        assertEquals(0L, first.getAvailableBalance());
    }

    @Test
    void concurrentDebitDoesNotOverdraft() throws InterruptedException {
        accountService.credit(accountId, 10000L);

        int threads = 20;
        int success = runConcurrently(threads, () -> accountService.debit(accountId, 100L));

        assertEquals(threads, success);
        Account account = accountMapper.selectById(accountId);
        assertEquals(8000L, account.getAvailableBalance());
        assertEquals(0L, account.getFrozenBalance());
    }

    @Test
    void concurrentDebitRejectsOverdraft() throws InterruptedException {
        accountService.credit(accountId, 500L);

        int threads = 10;
        List<Exception> failures = new CopyOnWriteArrayList<>();
        int success = runConcurrently(threads, failures, () -> accountService.debit(accountId, 100L));

        assertEquals(5, success);
        assertEquals(5, failures.size());
        failures.forEach(e -> assertEquals(AccountErrorCode.ACCOUNT_INSUFFICIENT_BALANCE.getCode(),
                ((BusinessException) e).getErrorCode().getCode()));
        assertEquals(0L, accountMapper.selectById(accountId).getAvailableBalance());
    }

    @Test
    void freezeUnfreezeKeepsBalanceConsistent() {
        accountService.credit(accountId, 1000L);

        accountService.freeze(accountId, 400L);
        Account afterFreeze = accountMapper.selectById(accountId);
        assertEquals(600L, afterFreeze.getAvailableBalance());
        assertEquals(400L, afterFreeze.getFrozenBalance());

        accountService.unfreeze(accountId, 100L);
        Account afterUnfreeze = accountMapper.selectById(accountId);
        assertEquals(700L, afterUnfreeze.getAvailableBalance());
        assertEquals(300L, afterUnfreeze.getFrozenBalance());

        BusinessException e = assertThrows(BusinessException.class,
                () -> accountService.unfreeze(accountId, 500L));
        assertEquals(AccountErrorCode.INSUFFICIENT_FROZEN.getCode(), e.getErrorCode().getCode());
        Account unchanged = accountMapper.selectById(accountId);
        assertEquals(700L, unchanged.getAvailableBalance());
        assertEquals(300L, unchanged.getFrozenBalance());
    }

    @Test
    void rejectsNonPositiveAmount() {
        accountService.credit(accountId, 1000L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> accountService.debit(accountId, 0L));
        assertEquals(AccountErrorCode.AMOUNT_INVALID.getCode(), e.getErrorCode().getCode());
        assertThrows(BusinessException.class, () -> accountService.freeze(accountId, -1L));
        assertEquals(1000L, accountMapper.selectById(accountId).getAvailableBalance());
    }

    /** 并发执行 N 笔相同操作，返回成功笔数；所有失败异常收集到 failures。 */
    private int runConcurrently(int threads, List<Exception> failures, Runnable action) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    action.run();
                    success.incrementAndGet();
                } catch (Exception e) {
                    failures.add(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "并发任务未在限时内完成");
        pool.shutdown();
        return success.get();
    }

    /** 并发执行 N 笔相同操作（无失败预期），返回成功笔数。 */
    private int runConcurrently(int threads, Runnable action) throws InterruptedException {
        return runConcurrently(threads, new CopyOnWriteArrayList<>(), action);
    }
}
