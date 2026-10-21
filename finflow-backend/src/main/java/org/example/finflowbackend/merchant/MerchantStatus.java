package org.example.finflowbackend.merchant;

import org.example.finflowbackend.common.statemachine.StateMachine;

import java.util.Map;
import java.util.Set;

/**
 * 商户状态与合法转换表（单一事实源）。
 * 驳回与关闭统一落 CLOSED，原因记 merchant_status_log.reason。
 */
public enum MerchantStatus {

    PENDING, REVIEWING, ACTIVE, SUSPENDED, CLOSED;

    public static final StateMachine<MerchantStatus> MACHINE = StateMachine.of(Map.of(
            PENDING, Set.of(REVIEWING, CLOSED),
            REVIEWING, Set.of(ACTIVE, CLOSED),
            ACTIVE, Set.of(SUSPENDED, CLOSED),
            SUSPENDED, Set.of(ACTIVE, CLOSED)
    ));
}
