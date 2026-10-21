package org.example.finflowbackend.refund.event;

import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.common.event.DomainEventPublisher;
import org.example.finflowbackend.refund.entity.RefundOrder;
import org.springframework.stereotype.Component;

/**
 * 退款事件发布：按状态机推进的目标状态分流。
 * 发布时机由 DomainEventPublisher 保证（事务提交后），本类只负责构造事件。
 * 注意：调用方必须显式传 toStatus——refund 实体在 CAS 更新后仍停留 fromStatus（S16 踩坑）。
 */
@Component
public class RefundEventPublisher {

    private final DomainEventPublisher eventPublisher;

    public RefundEventPublisher(DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /** 状态推进成功后调用：SUCCESS 发布 RefundSucceeded，其余状态暂不发。 */
    public void publishFor(RefundOrder refund, String toStatus, String reason) {
        switch (toStatus) {
            case "SUCCESS" -> eventPublisher.publish(RefundTopics.TOPIC_REFUND_SUCCEEDED,
                    String.valueOf(refund.getId()), DomainEvent.of(RefundTopics.TYPE_REFUND_SUCCEEDED,
                            new RefundSucceededPayload(refund.getOrderId(), refund.getRefundNo(),
                                    refund.getPlatformOrderNo(), refund.getMerchantId(),
                                    refund.getRefundAmount(), refund.getCurrency(), refund.getRefundedAt())));
            default -> {
                // APPLYING/PROCESSING/FAILED/CLOSED 暂无下游事件
            }
        }
    }
}
