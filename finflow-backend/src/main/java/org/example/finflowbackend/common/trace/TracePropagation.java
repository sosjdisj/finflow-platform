package org.example.finflowbackend.common.trace;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 跨进程链路传播（M15）：把 Micrometer / OTel 的当前上下文与 Kafka 消息头互转。
 *
 * <p>HTTP 与 Feign 的 traceparent 由 Boot 的 observation 过滤器与 feign-micrometer 自动处理；
 * Kafka 没有内置转发，因此这里显式用 {@link Propagator}（W3C traceparent + tracestate）
 * 在发送端 inject、消费端 extract，保证一笔支付的事件流也落在同一个 traceId 下。</p>
 */
@Component
public class TracePropagation {

    /** Kafka Headers 适配：传播字段按 UTF-8 写入（traceparent 为 ASCII，兼容其他 baggage）。 */
    private static final Propagator.Setter<Headers> SETTER =
            (headers, key, value) -> headers.add(key, value.getBytes(StandardCharsets.UTF_8));

    private static final Propagator.Getter<Headers> GETTER =
            (headers, key) -> headerValue(headers.lastHeader(key));

    private final Propagator propagator;
    private final Tracer tracer;

    public TracePropagation(Propagator propagator, Tracer tracer) {
        this.propagator = propagator;
        this.tracer = tracer;
    }

    /**
     * 把当前 span 上下文写入 Kafka 消息头。
     * 定时任务 / 启动期等非请求线程没有 span，此时不写头，消费端按无链路处理。
     */
    public void inject(Headers headers) {
        Span current = tracer.currentSpan();
        if (current != null) {
            propagator.inject(current.context(), headers, SETTER);
        }
    }

    /**
     * 从 Kafka 消息头恢复上游上下文并开启消费 span。
     *
     * @return 成功恢复时返回需要关闭的消费作用域；消息头没有有效链路（如存量报文）返回 null
     */
    public ConsumerSpan continueSpan(Headers headers, String spanName) {
        Span.Builder builder = propagator.extract(headers, GETTER);
        if (builder == null) {
            return null;
        }
        Span span = builder.name(spanName).start();
        return new ConsumerSpan(span, tracer.withSpan(span));
    }

    private static String headerValue(Header header) {
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    /** 消费 span 作用域：关闭时先退出 MDC 作用域，再结束 span，避免线程池串 traceId。 */
    public static final class ConsumerSpan implements AutoCloseable {

        private final Span span;
        private final Tracer.SpanInScope scope;

        private ConsumerSpan(Span span, Tracer.SpanInScope scope) {
            this.span = span;
            this.scope = scope;
        }

        @Override
        public void close() {
            scope.close();
            span.end();
        }
    }
}
