package org.example.finflowbackend.refund.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.refund.RefundErrorCode;
import org.example.finflowbackend.refund.RefundStatus;
import org.example.finflowbackend.refund.entity.RefundOrder;
import org.example.finflowbackend.refund.entity.RefundOrderStateLog;
import org.example.finflowbackend.refund.event.RefundEventPublisher;
import org.example.finflowbackend.refund.mapper.RefundOrderMapper;
import org.example.finflowbackend.refund.mapper.RefundOrderStateLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 退款状态推进组件（铁律 2：状态机校验与留痕集中于此，业务编排不得散写状态判断）。
 * 推进 = 行级锁当前读（FOR UPDATE，拿最新已提交状态，规避事务快照过期）→ 状态机 checkOrThrow
 * → CAS 更新（where 当前状态）→ 留痕 + 事件（事务提交后），同事务；
 * 并发竞态后到者 CAS 失败拒绝。独立成 Bean 是为了让编排方（创建流程、渠道回调）经代理
 * 调用获得事务边界，避免同类内部调用绕过 @Transactional。
 */
@Service
public class RefundStatusService {

    private final RefundOrderMapper refundMapper;
    private final RefundOrderStateLogMapper stateLogMapper;
    private final RefundEventPublisher refundEventPublisher;

    public RefundStatusService(RefundOrderMapper refundMapper, RefundOrderStateLogMapper stateLogMapper,
                               RefundEventPublisher refundEventPublisher) {
        this.refundMapper = refundMapper;
        this.stateLogMapper = stateLogMapper;
        this.refundEventPublisher = refundEventPublisher;
    }

    /** 通用状态推进：合法转换校验 + CAS + 留痕；推进到 SUCCESS 时落成功时间并发事件。 */
    @Transactional
    public void transition(Long refundId, RefundStatus to, String reason, String operator) {
        RefundOrder current = requireForUpdate(refundId);
        String fromStatus = current.getStatus();
        RefundStatus.MACHINE.checkOrThrow(RefundStatus.valueOf(fromStatus), to,
                RefundErrorCode.ILLEGAL_STATUS_TRANSITION);
        int updated = refundMapper.update(null, Wrappers.<RefundOrder>update()
                .set("status", to.name())
                .set(to == RefundStatus.SUCCESS, "refunded_at", LocalDateTime.now())
                .eq("id", refundId)
                .eq("status", fromStatus));
        if (updated == 0) {
            throw new BusinessException(RefundErrorCode.ILLEGAL_STATUS_TRANSITION, "退款单状态已被并发修改，推进作废");
        }
        if (to == RefundStatus.SUCCESS) {
            current.setRefundedAt(LocalDateTime.now());
        }
        appendLog(refundId, fromStatus, to.name(), reason, operator);
        // 事务提交后发布；显式传目标状态
        refundEventPublisher.publishFor(current, to.name(), reason);
    }

    /**
     * 渠道受理成功：回填渠道要素并推进 APPLYING→PROCESSING，同一事务（PROCESSING 不发事件）。
     * 补受理场景（回调先于受理响应落库）下已 PROCESSING 视为幂等成功，避免污染外层事务。
     */
    @Transactional
    public void markAccepted(Long refundId, String channelCode, String channelRefundNo) {
        RefundOrder current = requireForUpdate(refundId);
        String fromStatus = current.getStatus();
        if (RefundStatus.PROCESSING.name().equals(fromStatus)) {
            return;
        }
        RefundStatus.MACHINE.checkOrThrow(RefundStatus.valueOf(fromStatus), RefundStatus.PROCESSING,
                RefundErrorCode.ILLEGAL_STATUS_TRANSITION);
        int updated = refundMapper.update(null, Wrappers.<RefundOrder>update()
                .set("status", RefundStatus.PROCESSING.name())
                .set("channel_code", channelCode)
                .set("channel_refund_no", channelRefundNo)
                .eq("id", refundId)
                .eq("status", fromStatus));
        if (updated == 0) {
            throw new BusinessException(RefundErrorCode.ILLEGAL_STATUS_TRANSITION, "退款单状态已被并发修改，推进作废");
        }
        appendLog(refundId, fromStatus, RefundStatus.PROCESSING.name(),
                "渠道受理退款: " + channelRefundNo, "CHANNEL:" + channelCode);
    }

    private void appendLog(Long refundId, String fromStatus, String toStatus, String reason, String operator) {
        RefundOrderStateLog log = new RefundOrderStateLog();
        log.setRefundId(refundId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setReason(reason);
        log.setOperator(operator);
        stateLogMapper.insert(log);
    }

    private RefundOrder requireForUpdate(Long refundId) {
        RefundOrder refund = refundMapper.selectForUpdate(refundId);
        if (refund == null) {
            throw new BusinessException(RefundErrorCode.REFUND_NOT_FOUND);
        }
        return refund;
    }
}
