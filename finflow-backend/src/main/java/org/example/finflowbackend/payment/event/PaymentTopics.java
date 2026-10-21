package org.example.finflowbackend.payment.event;

/**
 * 支付事件主题与事件类型常量。退款、结算事件由后续阶段在各自模块补充。
 */
public final class PaymentTopics {

    public static final String TOPIC_CREATED = "finflow.payment.created";
    public static final String TOPIC_SUCCEEDED = "finflow.payment.succeeded";
    public static final String TOPIC_FAILED = "finflow.payment.failed";

    public static final String TYPE_CREATED = "PAYMENT_CREATED";
    public static final String TYPE_SUCCEEDED = "PAYMENT_SUCCEEDED";
    public static final String TYPE_FAILED = "PAYMENT_FAILED";

    private PaymentTopics() {
    }
}
