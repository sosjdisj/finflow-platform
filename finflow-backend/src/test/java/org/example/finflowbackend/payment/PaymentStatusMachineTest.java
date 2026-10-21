package org.example.finflowbackend.payment;

import org.example.finflowbackend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 支付状态机单测：穷举全部状态对，覆盖所有合法与非法转换（S12 验收项）。
 */
class PaymentStatusMachineTest {

    /** 报告规定的合法转换：主链路 + 异常分支 + 超时关单（S15）+ 风控分支（S30）+ 退款链路。 */
    private static final Map<PaymentStatus, Set<PaymentStatus>> LEGAL = Map.of(
            PaymentStatus.CREATED, Set.of(PaymentStatus.PENDING, PaymentStatus.CLOSED),
            PaymentStatus.PENDING, Set.of(PaymentStatus.PROCESSING, PaymentStatus.FAILED,
                    PaymentStatus.CLOSED, PaymentStatus.REVIEW),
            PaymentStatus.REVIEW, Set.of(PaymentStatus.PENDING, PaymentStatus.FAILED),
            PaymentStatus.PROCESSING, Set.of(PaymentStatus.PAID, PaymentStatus.FAILED),
            PaymentStatus.PAID, Set.of(PaymentStatus.SETTLED, PaymentStatus.REFUNDING),
            PaymentStatus.REFUNDING, Set.of(PaymentStatus.REFUNDED, PaymentStatus.PAID));

    @Test
    void coversAllLegalAndIllegalTransitions() {
        for (PaymentStatus from : PaymentStatus.values()) {
            for (PaymentStatus to : PaymentStatus.values()) {
                boolean expected = from != to && LEGAL.getOrDefault(from, Set.of()).contains(to);
                assertEquals(expected, PaymentStatus.MACHINE.canTransfer(from, to), () -> from + " -> " + to);
            }
        }
    }

    @Test
    void checkOrThrowPassesLegalTransition() {
        assertDoesNotThrow(() -> PaymentStatus.MACHINE.checkOrThrow(
                PaymentStatus.PENDING, PaymentStatus.PROCESSING, PaymentErrorCode.ILLEGAL_STATUS_TRANSITION));
    }

    @Test
    void checkOrThrowRejectsIllegalTransition() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> PaymentStatus.MACHINE.checkOrThrow(
                        PaymentStatus.PAID, PaymentStatus.PENDING, PaymentErrorCode.ILLEGAL_STATUS_TRANSITION));

        assertEquals(PaymentErrorCode.ILLEGAL_STATUS_TRANSITION, error.getErrorCode());
    }
}
