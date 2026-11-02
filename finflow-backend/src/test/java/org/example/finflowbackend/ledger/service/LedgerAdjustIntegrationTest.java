package org.example.finflowbackend.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.finflowbackend.account.api.AccountErrorCode;
import org.example.finflowbackend.account.api.AccountOwnerType;
import org.example.finflowbackend.account.api.AccountSnapshot;
import org.example.finflowbackend.account.service.AccountService;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.ledger.api.LedgerEntry;
import org.example.finflowbackend.ledger.entity.AccountLedger;
import org.example.finflowbackend.ledger.mapper.AccountLedgerMapper;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S20 人工调账集成验收（依赖本地 MySQL/Redis，S39 换 Testcontainers）：
 * 调增/调减都走流水（铁律 3），remark 随流水留痕，金额为 0 与余额不足被拒绝（拒绝不留流水）。
 */
class LedgerAdjustIntegrationTest extends IntegrationTestBase {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountLedgerMapper ledgerMapper;

    @Test
    void adjustCreditAndDebitBothLeaveLedger() {
        AccountSnapshot account = accountService.getOrCreate(AccountOwnerType.MERCHANT,
                System.nanoTime(), "CNY");
        long before = account.availableBalance();

        LedgerEntry up = ledgerService.adjust(account.id(), 500L, "错账调增");
        assertEquals(before + 500L, accountService.getAccount(account.id()).getAvailableBalance());
        assertEquals(500L, up.amount());
        assertEquals(before, up.balanceBefore());
        assertEquals(before + 500L, up.balanceAfter());
        assertEquals("ADJUSTMENT", up.bizType());
        assertEquals("CONSOLE", up.source());
        assertEquals("错账调增", up.remark());
        assertTrue(up.bizNo().startsWith("ADJ"));

        LedgerEntry down = ledgerService.adjust(account.id(), -200L, "错账调减");
        assertEquals(before + 300L, accountService.getAccount(account.id()).getAvailableBalance());
        assertEquals(-200L, down.amount());
        assertEquals(before + 500L, down.balanceBefore());
        assertEquals(before + 300L, down.balanceAfter());
    }

    @Test
    void adjustZeroAmountRejected() {
        AccountSnapshot account = accountService.getOrCreate(AccountOwnerType.MERCHANT,
                System.nanoTime(), "CNY");
        BusinessException e = assertThrows(BusinessException.class,
                () -> ledgerService.adjust(account.id(), 0L, null));
        assertEquals(AccountErrorCode.ADJUST_AMOUNT_ZERO, e.getErrorCode());
    }

    @Test
    void adjustDebitInsufficientBalanceRejected() {
        AccountSnapshot account = accountService.getOrCreate(AccountOwnerType.MERCHANT,
                System.nanoTime(), "CNY");
        ledgerService.adjust(account.id(), 100L, null);
        long before = accountService.getAccount(account.id()).getAvailableBalance();

        BusinessException e = assertThrows(BusinessException.class,
                () -> ledgerService.adjust(account.id(), -999L, null));
        assertEquals(AccountErrorCode.ACCOUNT_INSUFFICIENT_BALANCE, e.getErrorCode());
        assertEquals(before, accountService.getAccount(account.id()).getAvailableBalance());
        // 拒绝的调账不留流水：该账户只有调增那一条 ADJUSTMENT
        assertEquals(1L, ledgerMapper.selectCount(new LambdaQueryWrapper<AccountLedger>()
                .eq(AccountLedger::getAccountId, account.id())));
    }
}
