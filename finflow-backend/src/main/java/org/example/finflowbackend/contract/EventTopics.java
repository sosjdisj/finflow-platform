package org.example.finflowbackend.contract;

/**
 * Kafka 主题常量的唯一出处（M04）：全工程只有本类出现 topic 字符串字面量。
 * 主题名一经发布不得修改（语义只增不改），新增事件在此追加常量并配套 contract payload。
 */
public final class EventTopics {

    // 支付
    public static final String PAYMENT_CREATED = "finflow.payment.created";
    public static final String PAYMENT_SUCCEEDED = "finflow.payment.succeeded";
    public static final String PAYMENT_FAILED = "finflow.payment.failed";

    // 退款
    public static final String REFUND_SUCCEEDED = "finflow.refund.succeeded";

    // 结算
    public static final String SETTLEMENT_SUCCEEDED = "finflow.settlement.succeeded";

    // 风控
    public static final String RISK_INTERCEPTED = "finflow.risk.intercepted";

    // 对账
    public static final String RECONCILIATION_ABNORMAL = "finflow.reconciliation.abnormal";

    // 商户通知配置（M16）：通知服务据此维护自己的投影，不再同步回查 merchant / merchant_api_key
    public static final String MERCHANT_NOTIFY_CONFIG = "finflow.merchant.notify-config.changed";

    private EventTopics() {
    }
}
