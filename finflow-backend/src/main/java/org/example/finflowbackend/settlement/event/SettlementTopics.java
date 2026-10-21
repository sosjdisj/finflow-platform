package org.example.finflowbackend.settlement.event;

/**
 * 结算事件主题与事件类型常量（沿 S16 事件总线约定，主题按模块划分）。
 * S25 只发结算完成事件；下游（对账、通知）按需接入，本步不写消费者。
 */
public final class SettlementTopics {

    public static final String TOPIC_SETTLEMENT_SUCCEEDED = "finflow.settlement.succeeded";

    public static final String TYPE_SETTLEMENT_SUCCEEDED = "SETTLEMENT_SUCCEEDED";

    private SettlementTopics() {
    }
}
