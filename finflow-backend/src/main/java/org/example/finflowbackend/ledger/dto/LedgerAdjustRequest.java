package org.example.finflowbackend.ledger.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 人工调账请求：amount 带符号（正调增、负调减，分），必须走流水。
 */
public record LedgerAdjustRequest(
        @NotNull Long accountId,
        @NotNull Long amount,
        @Size(max = 255) String remark) {
}
