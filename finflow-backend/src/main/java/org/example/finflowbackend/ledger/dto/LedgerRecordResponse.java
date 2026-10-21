package org.example.finflowbackend.ledger.dto;

import org.example.finflowbackend.ledger.entity.AccountLedger;

import java.time.LocalDateTime;

/**
 * 资金流水视图：amount 带符号（正=入账，负=出账），余额均为分。
 */
public record LedgerRecordResponse(Long id, Long accountId, String bizType, String bizNo, Long amount,
                                   Long balanceBefore, Long balanceAfter, String currency,
                                   String source, String remark, LocalDateTime createdAt) {

    public static LedgerRecordResponse from(AccountLedger ledger) {
        return new LedgerRecordResponse(ledger.getId(), ledger.getAccountId(), ledger.getBizType(),
                ledger.getBizNo(), ledger.getAmount(), ledger.getBalanceBefore(), ledger.getBalanceAfter(),
                ledger.getCurrency(), ledger.getSource(), ledger.getRemark(), ledger.getCreatedAt());
    }
}
