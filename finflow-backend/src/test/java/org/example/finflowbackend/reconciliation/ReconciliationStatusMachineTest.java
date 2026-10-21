package org.example.finflowbackend.reconciliation;

import org.example.finflowbackend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 对账任务状态机单测：穷举合法与非法转换（S28 扩展时同步扩充）。
 */
class ReconciliationStatusMachineTest {

    private static final Map<ReconciliationStatus, Set<ReconciliationStatus>> LEGAL = Map.of(
            ReconciliationStatus.PENDING, Set.of(ReconciliationStatus.PARSING, ReconciliationStatus.FAILED),
            ReconciliationStatus.PARSING, Set.of(ReconciliationStatus.COMPLETED, ReconciliationStatus.FAILED));

    @Test
    void legalTransitionsPass() {
        LEGAL.forEach((from, tos) -> tos.forEach(to ->
                assertDoesNotThrow(() -> ReconciliationStatus.MACHINE.checkOrThrow(from, to,
                        ReconciliationErrorCode.ILLEGAL_STATUS_TRANSITION))));
    }

    @Test
    void illegalTransitionsRejected() {
        for (ReconciliationStatus from : ReconciliationStatus.values()) {
            for (ReconciliationStatus to : ReconciliationStatus.values()) {
                boolean legal = LEGAL.getOrDefault(from, Set.of()).contains(to);
                if (legal) {
                    continue;
                }
                BusinessException error = assertThrows(BusinessException.class,
                        () -> ReconciliationStatus.MACHINE.checkOrThrow(from, to,
                                ReconciliationErrorCode.ILLEGAL_STATUS_TRANSITION),
                        from + " -> " + to + " 应为非法转换");
                assertEquals(ReconciliationErrorCode.ILLEGAL_STATUS_TRANSITION, error.getErrorCode());
            }
        }
    }
}
