package org.example.finflowbackend.reconciliation;

import org.example.finflowbackend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 差异单状态机单测：穷举合法与非法转换。
 * 合法：PENDING → CONFIRMED / IGNORED；CONFIRMED → CLOSED；其余（含自反、终态再流转）均非法。
 */
class ReconciliationDifferenceStatusMachineTest {

    private static final Map<ReconciliationDifferenceStatus, Set<ReconciliationDifferenceStatus>> LEGAL = Map.of(
            ReconciliationDifferenceStatus.PENDING,
            Set.of(ReconciliationDifferenceStatus.CONFIRMED, ReconciliationDifferenceStatus.IGNORED),
            ReconciliationDifferenceStatus.CONFIRMED, Set.of(ReconciliationDifferenceStatus.CLOSED));

    @Test
    void legalTransitionsPass() {
        LEGAL.forEach((from, tos) -> tos.forEach(to ->
                assertDoesNotThrow(() -> ReconciliationDifferenceStatus.MACHINE.checkOrThrow(from, to,
                        ReconciliationErrorCode.DIFFERENCE_STATUS_INVALID))));
    }

    @Test
    void illegalTransitionsRejected() {
        for (ReconciliationDifferenceStatus from : ReconciliationDifferenceStatus.values()) {
            for (ReconciliationDifferenceStatus to : ReconciliationDifferenceStatus.values()) {
                if (LEGAL.getOrDefault(from, Set.of()).contains(to)) {
                    continue;
                }
                BusinessException error = assertThrows(BusinessException.class,
                        () -> ReconciliationDifferenceStatus.MACHINE.checkOrThrow(from, to,
                                ReconciliationErrorCode.DIFFERENCE_STATUS_INVALID),
                        from + " -> " + to + " 应为非法转换");
                assertEquals(ReconciliationErrorCode.DIFFERENCE_STATUS_INVALID, error.getErrorCode());
            }
        }
    }
}
