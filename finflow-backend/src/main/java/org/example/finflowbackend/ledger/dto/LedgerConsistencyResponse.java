package org.example.finflowbackend.ledger.dto;

/**
 * 账户流水一致性校验结果。
 */
public record LedgerConsistencyResponse(Long accountId, Long currentBalance, Long ledgerSum,
                                        boolean consistent) {
}
