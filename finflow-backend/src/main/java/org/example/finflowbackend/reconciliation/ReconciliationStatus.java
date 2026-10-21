package org.example.finflowbackend.reconciliation;

import org.example.finflowbackend.common.statemachine.StateMachine;

import java.util.Map;
import java.util.Set;

/**
 * 对账任务状态机（S27 输入侧）。
 * 上传即建任务 PENDING，同步解析经 PARSING 落 COMPLETED（统计行数）；
 * 解析失败任务终止为 FAILED（记原因）——同步解析的事务回滚会把状态拉回 PENDING，
 * 故 PENDING 也可直达 FAILED（FAILED 为终止态，S28 如需重解析再扩转换）。
 * S28 匹配引擎在此基础上扩展后续状态。
 */
public enum ReconciliationStatus {

    PENDING, PARSING, COMPLETED, FAILED;

    public static final StateMachine<ReconciliationStatus> MACHINE = StateMachine.of(Map.of(
            PENDING, Set.of(PARSING, FAILED),
            PARSING, Set.of(COMPLETED, FAILED)
    ));
}
