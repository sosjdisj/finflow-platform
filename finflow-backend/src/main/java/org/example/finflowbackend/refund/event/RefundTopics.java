package org.example.finflowbackend.refund.event;

/**
 * 退款事件主题与事件类型常量（沿 S16 事件总线约定，主题按模块划分）。
 * 退款失败暂不发事件（无下游关注），Webhook 阶段再按需补充。
 */
public final class RefundTopics {

    public static final String TOPIC_REFUND_SUCCEEDED = "finflow.refund.succeeded";

    public static final String TYPE_REFUND_SUCCEEDED = "REFUND_SUCCEEDED";

    private RefundTopics() {
    }
}
