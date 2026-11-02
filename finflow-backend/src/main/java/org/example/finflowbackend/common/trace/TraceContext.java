package org.example.finflowbackend.common.trace;

import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * traceId 唯一事实源（S40 / M15）：MDC key、请求属性 key 与读取方法集中在此。
 *
 * <p>HTTP 请求内以 {@link #ATTRIBUTE} 为准：TraceIdFilter 在链首解析一次后写入请求属性，
 * 统一响应体读取它，不受后续 observation 过滤器改写 MDC 的影响（两者在真实部署中本就同值）。
 * 非 Web 线程（Kafka 消费 / 定时任务）回退到 MDC。</p>
 */
public final class TraceContext {

    /** MDC key，与 logback pattern 的 %X{traceId} 对应。 */
    public static final String KEY = "traceId";

    /** 请求属性 key：一次请求内解析出的 traceId，ApiResponse 与过滤器共用。 */
    public static final String ATTRIBUTE = TraceContext.class.getName() + ".traceId";

    private TraceContext() {
    }

    /** 当前请求的 traceId；非 Web 线程（定时任务 / Kafka 消费者）返回 MDC 中的值或 null。 */
    public static String current() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            Object traceId = attributes.getRequest().getAttribute(ATTRIBUTE);
            if (traceId != null) {
                return traceId.toString();
            }
        }
        return MDC.get(KEY);
    }
}
