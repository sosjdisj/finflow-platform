package org.example.finflowbackend.settlement.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.common.event.DomainEventPublisher;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.settlement.SettlementErrorCode;
import org.example.finflowbackend.settlement.SettlementStatus;
import org.example.finflowbackend.settlement.entity.SettlementOrder;
import org.example.finflowbackend.settlement.entity.SettlementOrderStateLog;
import org.example.finflowbackend.settlement.event.SettlementSucceededPayload;
import org.example.finflowbackend.settlement.event.SettlementTopics;
import org.example.finflowbackend.settlement.mapper.SettlementOrderMapper;
import org.example.finflowbackend.settlement.mapper.SettlementOrderStateLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 结算单状态推进组件（铁律 2：状态机校验与留痕集中于此，业务编排不得散写状态判断）。
 * 推进 = 行级锁当前读（FOR UPDATE，规避事务快照过期）→ 状态机 checkOrThrow → CAS 更新
 * （where 当前状态，并发后到者拒绝）→ 留痕 + 事件（事务提交后），同一事务。
 * 注意：执行链路中「动账 + 推进 SUCCESS」必须同一事务（铁律 3），故动账方在自己的事务里
 * 调本组件（REQUIRED 传播加入当前事务），本组件不感知动账。
 */
@Service
public class SettlementStatusService {

    private final SettlementOrderMapper settlementOrderMapper;
    private final SettlementOrderStateLogMapper stateLogMapper;
    private final DomainEventPublisher eventPublisher;

    public SettlementStatusService(SettlementOrderMapper settlementOrderMapper,
                                   SettlementOrderStateLogMapper stateLogMapper,
                                   DomainEventPublisher eventPublisher) {
        this.settlementOrderMapper = settlementOrderMapper;
        this.stateLogMapper = stateLogMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 通用状态推进。推进到 APPROVED 落审核人与时间；到 SUCCESS 落执行时间并发结算完成事件；
     * 到 FAILED 落失败原因。operator：审核人为登录名，系统动作为 SYSTEM。
     */
    @Transactional
    public void transition(Long settlementId, SettlementStatus to, String reason, String operator) {
        SettlementOrder current = requireForUpdate(settlementId);
        SettlementStatus from = SettlementStatus.valueOf(current.getStatus());
        SettlementStatus.MACHINE.checkOrThrow(from, to, SettlementErrorCode.ILLEGAL_STATUS_TRANSITION);
        int updated = settlementOrderMapper.update(null, Wrappers.<SettlementOrder>update()
                .set("status", to.name())
                .set(to == SettlementStatus.APPROVED, "approved_at", LocalDateTime.now())
                .set(to == SettlementStatus.APPROVED, "approved_by", operator)
                .set(to == SettlementStatus.SUCCESS, "executed_at", LocalDateTime.now())
                .set(to == SettlementStatus.FAILED, "fail_reason", reason)
                .eq("id", settlementId)
                .eq("status", from.name()));
        if (updated == 0) {
            throw new BusinessException(SettlementErrorCode.ILLEGAL_STATUS_TRANSITION,
                    "结算单状态已被并发修改，推进作废");
        }
        appendLog(settlementId, from.name(), to.name(), reason, operator);
        // 事务提交后发布；显式传目标状态（CAS 后实体仍停留 fromStatus，S16 踩坑）
        if (to == SettlementStatus.SUCCESS) {
            eventPublisher.publish(SettlementTopics.TOPIC_SETTLEMENT_SUCCEEDED,
                    String.valueOf(current.getId()),
                    DomainEvent.of(SettlementTopics.TYPE_SETTLEMENT_SUCCEEDED,
                            new SettlementSucceededPayload(current.getSettleNo(), current.getMerchantId(),
                                    current.getSettleDate(), current.getTotalAmount(), current.getCurrency(),
                                    LocalDateTime.now())));
        }
    }

    private void appendLog(Long settlementId, String fromStatus, String toStatus, String reason, String operator) {
        SettlementOrderStateLog log = new SettlementOrderStateLog();
        log.setSettlementId(settlementId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setReason(reason);
        log.setOperator(operator);
        stateLogMapper.insert(log);
    }

    private SettlementOrder requireForUpdate(Long settlementId) {
        SettlementOrder order = settlementOrderMapper.selectForUpdate(settlementId);
        if (order == null) {
            throw new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
        }
        return order;
    }
}
