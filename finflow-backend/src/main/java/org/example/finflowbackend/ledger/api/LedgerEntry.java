package org.example.finflowbackend.ledger.api;

import org.example.finflowbackend.ledger.entity.AccountLedger;

import java.time.LocalDateTime;

/**
 * 流水条目快照：跨模块只传快照，不传流水实体（M03 边界约定）。
 */
public record LedgerEntry(Long id, Long accountId, String bizType, String bizNo, long amount,
                          long balanceBefore, long balanceAfter, String currency,
                          String source, String remark, LocalDateTime createdAt) {

    public static LedgerEntry from(AccountLedger ledger) {
        return new LedgerEntry(ledger.getId(), ledger.getAccountId(), ledger.getBizType(), ledger.getBizNo(),
                ledger.getAmount(), ledger.getBalanceBefore(), ledger.getBalanceAfter(), ledger.getCurrency(),
                ledger.getSource(), ledger.getRemark(), ledger.getCreatedAt());
    }
}
