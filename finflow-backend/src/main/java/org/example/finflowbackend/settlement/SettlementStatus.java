package org.example.finflowbackend.settlement;

import org.example.finflowbackend.common.statemachine.StateMachine;

import java.util.Map;
import java.util.Set;

/**
 * 结算单状态机。
 * 生成链路（S24）：PENDING（占位插入）→ CALCULATED（明细写入并校验汇总后）。
 * 审核执行链路（S25）：CALCULATED → APPROVED（审核通过）→ PROCESSING（发起执行）
 * → SUCCESS（入账完成）/ FAILED（入账失败记原因）；FAILED → PROCESSING 支持重试。
 */
public enum SettlementStatus {

    PENDING, CALCULATED, APPROVED, PROCESSING, SUCCESS, FAILED;

    public static final StateMachine<SettlementStatus> MACHINE = StateMachine.of(Map.of(
            PENDING, Set.of(CALCULATED),
            CALCULATED, Set.of(APPROVED),
            APPROVED, Set.of(PROCESSING),
            PROCESSING, Set.of(SUCCESS, FAILED),
            FAILED, Set.of(PROCESSING)
    ));
}
