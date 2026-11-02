package org.example.finflowbackend.common.trace;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * M15：Kafka 消息头与 Micrometer 上下文的转换——无 span 不写头、有 span 注入、
 * 无链路不创建消费作用域、有链路则关闭时结束 span。
 */
class TracePropagationTest {

    private final Propagator propagator = mock(Propagator.class);
    private final Tracer tracer = mock(Tracer.class);
    private final TracePropagation tracePropagation = new TracePropagation(propagator, tracer);

    @Test
    void doesNotInjectWithoutCurrentSpan() {
        when(tracer.currentSpan()).thenReturn(null);

        tracePropagation.inject(new RecordHeaders());

        verifyNoInteractions(propagator);
    }

    @Test
    void injectsCurrentSpanContext() {
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);

        Headers headers = new RecordHeaders();
        tracePropagation.inject(headers);

        verify(propagator).inject(eq(context), eq(headers), any());
    }

    @Test
    void returnsNullWhenHeadersCarryNoTrace() {
        when(propagator.extract(any(Headers.class), any())).thenReturn(null);

        assertNull(tracePropagation.continueSpan(new RecordHeaders(), "kafka.consume test"));
    }

    @Test
    void continuesExtractedContextAndEndsSpanOnClose() {
        Span.Builder builder = mock(Span.Builder.class);
        Span span = mock(Span.class);
        Tracer.SpanInScope scope = mock(Tracer.SpanInScope.class);
        when(propagator.extract(any(Headers.class), any())).thenReturn(builder);
        when(builder.name("kafka.consume test")).thenReturn(builder);
        when(builder.start()).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(scope);

        TracePropagation.ConsumerSpan consumerSpan =
                tracePropagation.continueSpan(new RecordHeaders(), "kafka.consume test");

        assertNotNull(consumerSpan);
        consumerSpan.close();
        verify(scope).close();
        verify(span).end();
    }
}
