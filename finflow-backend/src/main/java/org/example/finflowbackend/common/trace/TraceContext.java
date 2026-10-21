package org.example.finflowbackend.common.trace;

import org.slf4j.MDC;

/**
 * traceId 唯一事实源（S40）：MDC key 与读取方法集中在此，
 * {@link TraceIdFilter} 写入、ApiResponse 统一响应读取、日志 pattern 的 %X{traceId} 三方共用。
 */
public final class TraceContext {

    /** MDC key，与 logging.pattern.console 中的 %X{traceId} 对应。 */
    public static final String KEY = "traceId";

    private TraceContext() {
    }

    /** 当前请求的 traceId；非 Web 线程（定时任务 / Kafka 消费者）返回 null。 */
    public static String current() {
        return MDC.get(KEY);
    }
}
