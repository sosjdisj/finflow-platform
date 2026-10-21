package org.example.finflowbackend.reconciliation.event;

/**
 * 对账事件主题与事件类型常量（S33 通知接入）：匹配完成发现差异时发布，供通知中心消费。
 */
public final class ReconciliationTopics {

    public static final String TOPIC_ABNORMAL = "finflow.reconciliation.abnormal";

    public static final String TYPE_RECONCILIATION_DIFFERENCE = "RECONCILIATION_DIFFERENCE";

    private ReconciliationTopics() {
    }
}
