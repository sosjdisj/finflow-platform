package org.example.finflowbackend.refund;

import org.example.finflowbackend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 退款状态机单测：穷举全部状态对，覆盖所有合法与非法转换（S21 验收项）。
 */
class RefundStatusMachineTest {

    /** 报告规定的合法转换：申请 → 处理中 → 成功/失败；申请 → 失败（渠道拒绝）/关闭（预留终态）。 */
    private static final Map<RefundStatus, Set<RefundStatus>> LEGAL = Map.of(
            RefundStatus.APPLYING, Set.of(RefundStatus.PROCESSING, RefundStatus.FAILED, RefundStatus.CLOSED),
            RefundStatus.PROCESSING, Set.of(RefundStatus.SUCCESS, RefundStatus.FAILED));

    @Test
    void coversAllLegalAndIllegalTransitions() {
        for (RefundStatus from : RefundStatus.values()) {
            for (RefundStatus to : RefundStatus.values()) {
                boolean expected = from != to && LEGAL.getOrDefault(from, Set.of()).contains(to);
                assertEquals(expected, RefundStatus.MACHINE.canTransfer(from, to), () -> from + " -> " + to);
            }
        }
    }

    @Test
    void checkOrThrowPassesLegalTransition() {
        assertDoesNotThrow(() -> RefundStatus.MACHINE.checkOrThrow(
                RefundStatus.APPLYING, RefundStatus.PROCESSING, RefundErrorCode.ILLEGAL_STATUS_TRANSITION));
    }

    @Test
    void checkOrThrowRejectsIllegalTransition() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> RefundStatus.MACHINE.checkOrThrow(
                        RefundStatus.SUCCESS, RefundStatus.PROCESSING, RefundErrorCode.ILLEGAL_STATUS_TRANSITION));

        assertEquals(RefundErrorCode.ILLEGAL_STATUS_TRANSITION, error.getErrorCode());
    }
}
