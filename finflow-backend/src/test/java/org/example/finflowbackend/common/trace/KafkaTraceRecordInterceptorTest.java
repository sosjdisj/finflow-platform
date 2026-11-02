package org.example.finflowbackend.common.trace;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M15：Kafka 消费拦截器——每条消息开启一次作用域，afterRecord 必定关闭（线程池复用不能串 traceId）。
 */
class KafkaTraceRecordInterceptorTest {

    private final TracePropagation tracePropagation = mock(TracePropagation.class);
    private final KafkaTraceRecordInterceptor interceptor = new KafkaTraceRecordInterceptor(tracePropagation);

    private static ConsumerRecord<Object, Object> record() {
        return new ConsumerRecord<>("payment.succeeded", 0, 0L, "key", "{}");
    }

    @Test
    void closesSpanAfterEachRecord() {
        TracePropagation.ConsumerSpan span = mock(TracePropagation.ConsumerSpan.class);
        when(tracePropagation.continueSpan(any(), anyString())).thenReturn(span);

        ConsumerRecord<Object, Object> record = record();
        interceptor.intercept(record, null);
        interceptor.afterRecord(record, null);

        verify(span).close();
    }

    @Test
    void doesNothingWhenRecordHasNoTraceHeaders() {
        when(tracePropagation.continueSpan(any(), anyString())).thenReturn(null);

        ConsumerRecord<Object, Object> record = record();
        interceptor.intercept(record, null);
        interceptor.afterRecord(record, null);

        verify(tracePropagation).continueSpan(any(), anyString());
    }
}
