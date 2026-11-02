package org.example.finflowgateway.web;

import org.example.finflowgateway.api.GatewayErrorCode;
import org.example.finflowgateway.filter.OpenApiEnvelopeFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * 网关统一错误出口：状态码、47xxx、traceId 与版本头一次写齐。
 * 只写网关自己产生的错误；backend 返回的业务错误保持原样透传。
 */
@Component
public class GatewayResponseWriter {

    public static final String HEADER_VERSION = "X-OpenApi-Version";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String VERSION = "v1";

    private final ObjectMapper objectMapper;

    public GatewayResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, GatewayErrorCode errorCode, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(HttpStatusCode.valueOf(errorCode.getHttpStatus()));
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        response.getHeaders().set(HEADER_VERSION, VERSION);
        Object traceId = exchange.getAttributes().get(OpenApiEnvelopeFilter.TRACE_ID_ATTRIBUTE);
        GatewayResponse body = new GatewayResponse(errorCode.getCode(),
                message == null ? errorCode.getMessage() : message, null, traceId == null ? null : traceId.toString());
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
