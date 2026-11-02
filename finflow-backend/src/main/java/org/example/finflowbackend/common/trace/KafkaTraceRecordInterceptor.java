package org.example.finflowbackend.common.trace;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

/**
 * Kafka 消费端链路恢复（M15）：每条消息处理前从消息头恢复上游 traceparent 并开启消费 span，
 * 处理完立即关闭。Boot 4 的 {@code KafkaAnnotationDrivenConfiguration} 会自动采用容器内唯一的
 * {@link RecordInterceptor} bean，所有 {@code @KafkaListener} 生效，无需逐个容器手工装配。
 *
 * <p>作用域保存在 ThreadLocal：监听容器线程池复用线程，afterRecord 必须清理，否则 traceId 串到下一条消息。</p>
 */
@Component
public class KafkaTraceRecordInterceptor implements RecordInterceptor<Object, Object> {

    private static final ThreadLocal<TracePropagation.ConsumerSpan> CURRENT_SPAN = new ThreadLocal<>();

    private final TracePropagation tracePropagation;

    public KafkaTraceRecordInterceptor(TracePropagation tracePropagation) {
        this.tracePropagation = tracePropagation;
    }

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        TracePropagation.ConsumerSpan span =
                tracePropagation.continueSpan(record.headers(), "kafka.consume " + record.topic());
        if (span != null) {
            CURRENT_SPAN.set(span);
        }
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        TracePropagation.ConsumerSpan span = CURRENT_SPAN.get();
        CURRENT_SPAN.remove();
        if (span != null) {
            span.close();
        }
    }
}
