package org.example.finflowbackend.risk.event;

/**
 * 风控事件主题与事件类型常量（S33 通知接入）：拦截（REVIEW/REJECT）时发布，供通知中心消费。
 */
public final class RiskTopics {

    public static final String TOPIC_INTERCEPTED = "finflow.risk.intercepted";

    public static final String TYPE_RISK_INTERCEPTED = "RISK_INTERCEPTED";

    private RiskTopics() {
    }
}
