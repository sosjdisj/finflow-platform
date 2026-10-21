package org.example.finflowbackend.payment;

import org.example.finflowbackend.common.statemachine.StateMachine;

import java.util.Map;
import java.util.Set;

/**
 * 支付订单状态与合法转换表（单一事实源）。
 * 主链路 CREATED → PENDING → PROCESSING → PAID → SETTLED；
 * 异常分支 PENDING/PROCESSING → FAILED；超时关单 CREATED/PENDING → CLOSED；
 * 风控分支 PENDING → REVIEW（S30 命中待审），人工通过回 PENDING 重新受理、拒绝置 FAILED；
 * 退款链路 PAID → REFUNDING → REFUNDED；退款失败释放额度后 REFUNDING → PAID（回退，S21）。
 */
public enum PaymentStatus {

    CREATED, PENDING, PROCESSING, PAID, SETTLED, FAILED, CLOSED, REFUNDING, REFUNDED, REVIEW;

    public static final StateMachine<PaymentStatus> MACHINE = StateMachine.of(Map.of(
            CREATED, Set.of(PENDING, CLOSED),
            PENDING, Set.of(PROCESSING, FAILED, CLOSED, REVIEW),
            REVIEW, Set.of(PENDING, FAILED),
            PROCESSING, Set.of(PAID, FAILED),
            PAID, Set.of(SETTLED, REFUNDING),
            REFUNDING, Set.of(REFUNDED, PAID)
    ));
}
