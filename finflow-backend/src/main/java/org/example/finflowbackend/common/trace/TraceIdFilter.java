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
 * 链路追踪过滤器（S40）：一个请求一个 traceId，贯穿日志（MDC）并写入响应头与统一响应体。
 * 上游（Nginx / 外部网关）可传 {@code X-Trace-Id} 透传串联全链路，无则本端生成。
 * 排在所有过滤器最前，保证 Security 401/403 等早期响应也带 traceId。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";

    /** 防止异常超长头注入日志；超过视为不可信，重新生成。 */
    private static final int MAX_HEADER_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = request.getHeader(HEADER);
        if (traceId == null || traceId.isBlank() || traceId.length() > MAX_HEADER_LENGTH) {
            traceId = newTraceId();
        }
        MDC.put(TraceContext.KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 线程复用，必须清理，避免 traceId 串到池内其他请求日志
            MDC.remove(TraceContext.KEY);
        }
    }

    private String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
