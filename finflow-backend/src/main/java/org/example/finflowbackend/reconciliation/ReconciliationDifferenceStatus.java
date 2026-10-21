package org.example.finflowbackend.reconciliation;

import org.example.finflowbackend.common.statemachine.StateMachine;

import java.util.Map;
import java.util.Set;

/**
 * 对账差异单状态机（S29 人工处理流）。
 * 发起即 PENDING（待审核）；确认选定处理方式后进 CONFIRMED；执行处理（调账/补单登记）成功即 CLOSED，
 * 失败整体回滚保持 CONFIRMED；误报可从 PENDING 直达 IGNORED。CLOSED / IGNORED 为终止态。
 */
public enum ReconciliationDifferenceStatus {

    PENDING, CONFIRMED, CLOSED, IGNORED;

    public static final StateMachine<ReconciliationDifferenceStatus> MACHINE = StateMachine.of(Map.of(
            PENDING, Set.of(CONFIRMED, IGNORED),
            CONFIRMED, Set.of(CLOSED)
    ));
}
