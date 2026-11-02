package org.example.finflowgateway.filter;

import org.example.finflowgateway.api.GatewayErrorCode;
import org.example.finflowgateway.api.GatewayException;
import org.example.finflowgateway.security.InternalHeaders;
import org.example.finflowgateway.web.GatewayResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 网关请求信封（M13）：链路 ID、版本头、可信头清洗、统一错误出口。
 *
 * <p>排在最前：先给请求生成/透传 X-Trace-Id（backend 的 TraceIdFilter 会沿用它，两端日志可串），
 * 再把商户自带的 X-Finflow-* 内部头清空——这两个头只能由网关验签后重新写入，否则等于让商户自证清白。
 * 网关自身产生的拒绝与未预期异常都在这里统一成 code / message / traceId / X-OpenApi-Version 结构。</p>
 */
@Component
public class OpenApiEnvelopeFilter implements GatewayFilter, Ordered {

    public static final String TRACE_ID_ATTRIBUTE = OpenApiEnvelopeFilter.class.getName() + ".traceId";

    private static final Logger log = LoggerFactory.getLogger(OpenApiEnvelopeFilter.class);

    /** 与 backend TraceIdFilter 同规则：超长视为不可信，重新生成。 */
    private static final int MAX_TRACE_ID_LENGTH = 64;

    private final GatewayResponseWriter responseWriter;

    public OpenApiEnvelopeFilter(GatewayResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = resolveTraceId(exchange.getRequest().getHeaders().getFirst(GatewayResponseWriter.HEADER_TRACE_ID));
        exchange.getAttributes().put(TRACE_ID_ATTRIBUTE, traceId);
        exchange.getResponse().getHeaders().set(GatewayResponseWriter.HEADER_TRACE_ID, traceId);
        exchange.getResponse().getHeaders().set(GatewayResponseWriter.HEADER_VERSION, GatewayResponseWriter.VERSION);

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(InternalHeaders.TOKEN);
                    headers.remove(InternalHeaders.MERCHANT_ID);
                    headers.set(GatewayResponseWriter.HEADER_TRACE_ID, traceId);
                })
                .build();
        ServerWebExchange forwarded = exchange.mutate().request(request).build();

        // defer：把后续过滤器同步抛出的异常也纳入统一错误出口
        return Mono.defer(() -> chain.filter(forwarded))
                .onErrorResume(GatewayException.class,
                        e -> responseWriter.write(forwarded, e.getErrorCode(), e.getMessage()))
                .onErrorResume(exception -> {
                    log.error("[OpenAPI网关] 未预期异常: path={}", request.getURI().getPath(), exception);
                    return responseWriter.write(forwarded, GatewayErrorCode.SYSTEM_ERROR, null);
                });
    }

    private String resolveTraceId(String incoming) {
        // S40 约定：上游显式传 X-Trace-Id 时透传，保证调用方拿到的链路 ID 不变；
        // M15：没有上游头时用 Micrometer Tracing 的 MDC traceId（与网关日志、traceparent 同源），
        // 最后才自造。正常商户调用不带 X-Trace-Id，四进程因此落在同一个 traceId 下。
        if (incoming != null && !incoming.isBlank() && incoming.length() <= MAX_TRACE_ID_LENGTH) {
            return incoming;
        }
        String tracingTraceId = MDC.get("traceId");
        if (tracingTraceId != null && !tracingTraceId.isBlank()) {
            return tracingTraceId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
