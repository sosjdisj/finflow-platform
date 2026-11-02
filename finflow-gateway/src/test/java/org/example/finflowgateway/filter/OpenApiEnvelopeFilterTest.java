package org.example.finflowgateway.filter;

import org.example.finflowgateway.api.GatewayErrorCode;
import org.example.finflowgateway.api.GatewayException;
import org.example.finflowgateway.security.InternalHeaders;
import org.example.finflowgateway.web.GatewayResponseWriter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M13 网关信封过滤器：可信头清洗、traceId 透传/生成、统一错误出口。
 */
class OpenApiEnvelopeFilterTest {

    private static final String PATH = "/open/v1/payments";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GatewayResponseWriter responseWriter = new GatewayResponseWriter(objectMapper);
    private final OpenApiEnvelopeFilter filter = new OpenApiEnvelopeFilter(responseWriter);

    @Test
    void stripsClientSuppliedTrustedHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("http://localhost" + PATH)
                .header(InternalHeaders.TOKEN, "forged-token")
                .header(InternalHeaders.MERCHANT_ID, "999")
                .header(GatewayResponseWriter.HEADER_TRACE_ID, "trace-in-1")
                .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = forwardedExchange -> {
            forwarded.set(forwardedExchange);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        ServerWebExchange result = forwarded.get();
        assertNotNull(result);
        assertNull(result.getRequest().getHeaders().getFirst(InternalHeaders.TOKEN));
        assertNull(result.getRequest().getHeaders().getFirst(InternalHeaders.MERCHANT_ID));
        assertEquals("trace-in-1", result.getRequest().getHeaders().getFirst(GatewayResponseWriter.HEADER_TRACE_ID));
        assertEquals("trace-in-1", result.getResponse().getHeaders().getFirst(GatewayResponseWriter.HEADER_TRACE_ID));
        assertEquals("v1", result.getResponse().getHeaders().getFirst(GatewayResponseWriter.HEADER_VERSION));
    }

    @Test
    void generatesTraceIdWhenIncomingIsTooLong() {
        String oversized = "x".repeat(80);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("http://localhost" + PATH)
                .header(GatewayResponseWriter.HEADER_TRACE_ID, oversized)
                .build());

        filter.filter(exchange, exchange2 -> Mono.empty()).block();

        String traceId = (String) exchange.getAttribute(OpenApiEnvelopeFilter.TRACE_ID_ATTRIBUTE);
        assertNotNull(traceId);
        assertNotEquals(oversized, traceId);
        assertEquals(32, traceId.length());
    }

    @Test
    void usesTracingTraceIdWhenIncomingHeaderMissing() {
        try {
            MDC.put("traceId", "otel-trace-123");
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("http://localhost" + PATH).build());

            filter.filter(exchange, exchange2 -> Mono.empty()).block();

            assertEquals("otel-trace-123",
                    exchange.getResponse().getHeaders().getFirst(GatewayResponseWriter.HEADER_TRACE_ID));
        } finally {
            MDC.remove("traceId");
        }
    }

    @Test
    void writesGatewayErrorAsUnifiedEnvelope() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("http://localhost" + PATH)
                .header(GatewayResponseWriter.HEADER_TRACE_ID, "trace-abc")
                .build());
        GatewayFilterChain chain = exchange2 -> Mono.error(new GatewayException(GatewayErrorCode.TIMESTAMP_EXPIRED));

        filter.filter(exchange, chain).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
        assertEquals("v1", exchange.getResponse().getHeaders().getFirst(GatewayResponseWriter.HEADER_VERSION));
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertEquals(47003, body.get("code").asInt());
        assertEquals("请求时间戳超出允许窗口", body.get("message").asString());
        assertEquals("trace-abc", body.get("traceId").asString());
        assertTrue(body.get("data").isNull());
    }

    @Test
    void mapsUnexpectedExceptionToSystemError() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("http://localhost" + PATH)
                .build());
        GatewayFilterChain chain = exchange2 -> Mono.error(new IllegalStateException("boom"));

        filter.filter(exchange, chain).block();

        assertEquals(500, exchange.getResponse().getStatusCode().value());
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertEquals(47019, body.get("code").asInt());
    }

    @Test
    void catchesSynchronouslyThrownErrors() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("http://localhost" + PATH)
                .build());
        GatewayFilterChain chain = exchange2 -> {
            throw new IllegalStateException("sync-boom");
        };

        filter.filter(exchange, chain).block();

        assertEquals(500, exchange.getResponse().getStatusCode().value());
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertEquals(47019, body.get("code").asInt());
    }
}
