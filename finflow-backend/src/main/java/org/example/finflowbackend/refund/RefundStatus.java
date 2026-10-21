package org.example.finflowbackend.refund;

import org.example.finflowbackend.common.statemachine.StateMachine;

import java.util.Map;
import java.util.Set;

/**
 * 退款单状态与合法转换表（单一事实源）。
 * 主链路 APPLYING → PROCESSING → SUCCESS；
 * 异常分支 APPLYING → FAILED（渠道拒绝受理）、PROCESSING → FAILED（渠道回调退款失败）；
 * CLOSED 为预留终态（受理超时对账关闭，本阶段不触发，先入转换表占位）。
 */
public enum RefundStatus {

    APPLYING, PROCESSING, SUCCESS, FAILED, CLOSED;

    public static final StateMachine<RefundStatus> MACHINE = StateMachine.of(Map.of(
            APPLYING, Set.of(PROCESSING, FAILED, CLOSED),
            PROCESSING, Set.of(SUCCESS, FAILED)
    ));
}