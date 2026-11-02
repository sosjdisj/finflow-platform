package org.example.finflowbackend.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪过滤器（S40 / M15）：一个请求一个 traceId，贯穿日志（MDC）并写入响应头与统一响应体。
 *
 * <p>M15 起 traceId 的事实源是 Micrometer Tracing：Boot 的 observation 过滤器（order = +1）
 * 先从入站 {@code traceparent} 恢复上下文并写入 MDC，本过滤器（order = +2）在其后读取，
 * 保证网关 / backend / 渠道 / 风控四个进程的日志是同一个 traceId。</p>
 *
 * <p>无 tracing 上下文时保持 S40 行为：优先沿用上游 {@code X-Trace-Id}（Nginx 或直连调用），
 * 否则生成 32 位十六进制。只有本过滤器自己写入 MDC 时才负责清理，避免误删 observation 过滤器
 * 建立的作用域。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";

    /** 防止异常超长头注入日志；超过视为不可信，重新生成。 */
    private static final int MAX_HEADER_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String previous = MDC.get(TraceContext.KEY);
        boolean mdcWritten = false;
        String traceId = resolve(request.getHeader(HEADER), previous);
        if (!traceId.equals(previous)) {
            MDC.put(TraceContext.KEY, traceId);
            mdcWritten = true;
        }
        // 请求内事实源：统一响应体据此回填 traceId，与日志 / 响应头保持一致
        request.setAttribute(TraceContext.ATTRIBUTE, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            if (mdcWritten) {
                // 线程复用，必须还原；MDC 原本为空时直接移除，避免 traceId 串到池内其他请求日志
                if (previous == null) {
                    MDC.remove(TraceContext.KEY);
                } else {
                    MDC.put(TraceContext.KEY, previous);
                }
            }
        }
    }

    /** 入站 X-Trace-Id 优先（S40 约定）；否则沿用 tracing 上下文，最后才生成。 */
    private String resolve(String incoming, String tracingTraceId) {
        if (incoming != null && !incoming.isBlank() && incoming.length() <= MAX_HEADER_LENGTH) {
            return incoming;
        }
        if (tracingTraceId != null && !tracingTraceId.isBlank()) {
            return tracingTraceId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
