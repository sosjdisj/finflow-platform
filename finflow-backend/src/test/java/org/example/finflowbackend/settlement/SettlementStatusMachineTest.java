package org.example.finflowbackend.settlement;

import org.example.finflowbackend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 结算单状态机单测：穷举合法与非法转换（S25 审核执行链路）。
 */
class SettlementStatusMachineTest {

    private static final Map<SettlementStatus, Set<SettlementStatus>> LEGAL = Map.of(
            SettlementStatus.PENDING, Set.of(SettlementStatus.CALCULATED),
            SettlementStatus.CALCULATED, Set.of(SettlementStatus.APPROVED),
            SettlementStatus.APPROVED, Set.of(SettlementStatus.PROCESSING),
            SettlementStatus.PROCESSING, Set.of(SettlementStatus.SUCCESS, SettlementStatus.FAILED),
            SettlementStatus.FAILED, Set.of(SettlementStatus.PROCESSING));

    @Test
    void coversAllLegalAndIllegalTransitions() {
        for (SettlementStatus from : SettlementStatus.values()) {
            for (SettlementStatus to : SettlementStatus.values()) {
                boolean expected = from != to && LEGAL.getOrDefault(from, Set.of()).contains(to);
                assertEquals(expected, SettlementStatus.MACHINE.canTransfer(from, to),
                        () -> from + " -> " + to);
            }
        }
    }

    @Test
    void checkOrThrowPassesLegalTransition() {
        assertDoesNotThrow(() -> SettlementStatus.MACHINE.checkOrThrow(
                SettlementStatus.PENDING, SettlementStatus.CALCULATED,
                SettlementErrorCode.ILLEGAL_STATUS_TRANSITION));
    }

    @Test
    void checkOrThrowRejectsIllegalTransition() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> SettlementStatus.MACHINE.checkOrThrow(
                        SettlementStatus.CALCULATED, SettlementStatus.PENDING,
                        SettlementErrorCode.ILLEGAL_STATUS_TRANSITION));
        assertEquals(SettlementErrorCode.ILLEGAL_STATUS_TRANSITION, error.getErrorCode());
    }
}
