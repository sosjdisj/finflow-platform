package org.example.finflowbackend.contract;

/**
 * 事件类型常量（DomainEvent.eventType 的取值）。
 * 消费端据此分流与投递外部报文；取值一经发布不得修改（语义只增不改）。
 */
public final class EventTypes {

    public static final String PAYMENT_CREATED = "PAYMENT_CREATED";
    public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String REFUND_SUCCEEDED = "REFUND_SUCCEEDED";
    public static final String SETTLEMENT_SUCCEEDED = "SETTLEMENT_SUCCEEDED";
    public static final String RISK_INTERCEPTED = "RISK_INTERCEPTED";
    public static final String RECONCILIATION_DIFFERENCE = "RECONCILIATION_DIFFERENCE";
    public static final String MERCHANT_NOTIFY_CONFIG_CHANGED = "MERCHANT_NOTIFY_CONFIG_CHANGED";

    private EventTypes() {
    }
}
