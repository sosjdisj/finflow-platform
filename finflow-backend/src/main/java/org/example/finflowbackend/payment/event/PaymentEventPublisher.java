package org.example.finflowbackend.payment.event;

import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.common.event.DomainEventPublisher;
import org.example.finflowbackend.payment.PaymentStatus;
import org.example.finflowbackend.payment.entity.PaymentOrder;
import org.springframework.stereotype.Component;

/**
 * 支付事件发布：按状态机推进的目标状态分流到对应主题。
 * 发布时机由 DomainEventPublisher 保证（事务提交后），本类只负责构造事件。
 */
@Component
public class PaymentEventPublisher {

    private final DomainEventPublisher eventPublisher;

    public PaymentEventPublisher(DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /** 状态推进成功后调用：按目标状态 toStatus 发布 Created/Succeeded/Failed 事件，其余状态不发。 */
    public void publishFor(PaymentOrder order, String toStatus, String reason) {
        switch (toStatus) {
            case "PENDING" -> eventPublisher.publish(PaymentTopics.TOPIC_CREATED,
                    String.valueOf(order.getId()), DomainEvent.of(PaymentTopics.TYPE_CREATED,
                            new PaymentCreatedPayload(order.getId(), order.getPlatformOrderNo(),
                                    order.getMerchantId(), order.getMerchantOrderNo(),
                                    order.getAmount(), order.getPaymentMethod())));
            case "PAID" -> {
                // 仅支付成功（PROCESSING→PAID）发 Succeeded；退款失败回退 REFUNDING→PAID 不重发（S21）
                if (PaymentStatus.PROCESSING.name().equals(order.getStatus())) {
                    eventPublisher.publish(PaymentTopics.TOPIC_SUCCEEDED,
                            String.valueOf(order.getId()), DomainEvent.of(PaymentTopics.TYPE_SUCCEEDED,
                                    new PaymentSucceededPayload(order.getId(), order.getPlatformOrderNo(),
                                            order.getAmount(), order.getCurrency(), order.getUpdatedAt())));
                }
            }
            case "FAILED", "CLOSED" -> eventPublisher.publish(PaymentTopics.TOPIC_FAILED,
                    String.valueOf(order.getId()), DomainEvent.of(PaymentTopics.TYPE_FAILED,
                            new PaymentFailedPayload(order.getId(), order.getPlatformOrderNo(),
                                    order.getAmount(), reason, order.getUpdatedAt())));
            default -> {
                // PROCESSING/SETTLED/REFUNDING/REFUNDED 事件由退款、结算阶段各自补充
            }
        }
    }
}
