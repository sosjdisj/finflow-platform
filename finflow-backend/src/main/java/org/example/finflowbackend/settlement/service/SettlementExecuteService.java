package org.example.finflowbackend.settlement.service;

import org.example.finflowbackend.audit.AuditRecorder;
import org.example.finflowbackend.common.api.ErrorCode;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.settlement.SettlementStatus;
import org.example.finflowbackend.settlement.entity.SettlementOrder;
import org.example.finflowbackend.settlement.mapper.SettlementOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 结算审核与执行编排入口（S25）。
 * approve：CALCULATED → APPROVED（人工审核，记审核人）。
 * execute：APPROVED/FAILED → PROCESSING（事务①互斥占位）→ 动账+SUCCESS（事务②，
 * 见 SettlementPostingService）；事务②失败 → 事务③标 FAILED 记原因后原样抛出。
 * 重试 = 对 FAILED 单再次调用 execute。重复/并发执行被事务①的状态机校验拒绝。
 * 本类方法自身无事务：三个事务边界分别在状态推进与动账组件内，避免长事务跨步骤。
 */
@Service
public class SettlementExecuteService {

    private static final Logger log = LoggerFactory.getLogger(SettlementExecuteService.class);
    private static final int FAIL_REASON_MAX = 255;

    private final SettlementStatusService statusService;
    private final SettlementPostingService postingService;
    private final SettlementOrderMapper settlementOrderMapper;
    private final AuditRecorder auditRecorder;

    public SettlementExecuteService(SettlementStatusService statusService,
                                    SettlementPostingService postingService,
                                    SettlementOrderMapper settlementOrderMapper,
                                    AuditRecorder auditRecorder) {
        this.statusService = statusService;
        this.postingService = postingService;
        this.settlementOrderMapper = settlementOrderMapper;
        this.auditRecorder = auditRecorder;
    }

    /** 审核通过：CALCULATED → APPROVED。 */
    public void approve(Long settlementId, String operator) {
        SettlementOrder before = settlementOrderMapper.selectById(settlementId);
        statusService.transition(settlementId, SettlementStatus.APPROVED, "审核通过", operator);
        auditRecorder.submit(before, Map.of("status", SettlementStatus.APPROVED.name()));
    }

    /** 执行打款：动账入商户账户。失败标 FAILED 并抛出原错误；FAILED 单可再次调用重试。 */
    public void execute(Long settlementId) {
        statusService.transition(settlementId, SettlementStatus.PROCESSING, "发起执行", "SYSTEM");
        // 金额/商户/币种为不可变字段，快照读即可；状态以事务②内的 CAS 为准
        SettlementOrder order = settlementOrderMapper.selectById(settlementId);
        try {
            postingService.post(order);
        } catch (BusinessException e) {
            markFailed(settlementId, e.getMessage());
            throw e;
        } catch (Exception e) {
            markFailed(settlementId, "执行异常: " + e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "结算执行失败: " + e.getMessage());
        }
    }

    /** 标 FAILED（独立事务，见状态组件）；标记本身失败不吞原异常，仅告警。 */
    private void markFailed(Long settlementId, String reason) {
        try {
            statusService.transition(settlementId, SettlementStatus.FAILED, truncate(reason), "SYSTEM");
        } catch (Exception ex) {
            log.warn("[结算执行] 标记 FAILED 未成功（可能已被并发处理）: settlementId={}", settlementId, ex);
        }
    }

    private String truncate(String reason) {
        if (reason == null) {
            return "未知原因";
        }
        return reason.length() <= FAIL_REASON_MAX ? reason : reason.substring(0, FAIL_REASON_MAX);
    }
}
