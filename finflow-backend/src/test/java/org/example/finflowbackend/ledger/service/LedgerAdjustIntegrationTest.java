package org.example.finflowbackend.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.finflowbackend.account.AccountErrorCode;
import org.example.finflowbackend.account.AccountOwnerType;
import org.example.finflowbackend.account.entity.Account;
import org.example.finflowbackend.account.service.AccountService;
import org.example.finflowbackend.common.exception.BusinessException;
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
        Account account = accountService.getOrCreate(AccountOwnerType.MERCHANT,
                System.nanoTime(), "CNY");
        long before = account.getAvailableBalance();

        AccountLedger up = ledgerService.adjust(account.getId(), 500L, "错账调增");
        assertEquals(before + 500L, accountService.getAccount(account.getId()).getAvailableBalance());
        assertEquals(500L, up.getAmount());
        assertEquals(before, up.getBalanceBefore());
        assertEquals(before + 500L, up.getBalanceAfter());
        assertEquals("ADJUSTMENT", up.getBizType());
        assertEquals("CONSOLE", up.getSource());
        assertEquals("错账调增", up.getRemark());
        assertTrue(up.getBizNo().startsWith("ADJ"));

        AccountLedger down = ledgerService.adjust(account.getId(), -200L, "错账调减");
        assertEquals(before + 300L, accountService.getAccount(account.getId()).getAvailableBalance());
        assertEquals(-200L, down.getAmount());
        assertEquals(before + 500L, down.getBalanceBefore());
        assertEquals(before + 300L, down.getBalanceAfter());
    }

    @Test
    void adjustZeroAmountRejected() {
        Account account = accountService.getOrCreate(AccountOwnerType.MERCHANT,
                System.nanoTime(), "CNY");
        BusinessException e = assertThrows(BusinessException.class,
                () -> ledgerService.adjust(account.getId(), 0L, null));
        assertEquals(AccountErrorCode.ADJUST_AMOUNT_ZERO, e.getErrorCode());
    }

    @Test
    void adjustDebitInsufficientBalanceRejected() {
        Account account = accountService.getOrCreate(AccountOwnerType.MERCHANT,
                System.nanoTime(), "CNY");
        ledgerService.adjust(account.getId(), 100L, null);
        long before = accountService.getAccount(account.getId()).getAvailableBalance();

        BusinessException e = assertThrows(BusinessException.class,
                () -> ledgerService.adjust(account.getId(), -999L, null));
        assertEquals(AccountErrorCode.ACCOUNT_INSUFFICIENT_BALANCE, e.getErrorCode());
        assertEquals(before, accountService.getAccount(account.getId()).getAvailableBalance());
        // 拒绝的调账不留流水：该账户只有调增那一条 ADJUSTMENT
        assertEquals(1L, ledgerMapper.selectCount(new LambdaQueryWrapper<AccountLedger>()
                .eq(AccountLedger::getAccountId, account.getId())));
    }
}
