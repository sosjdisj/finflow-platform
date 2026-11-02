package org.example.finflowgateway.filter;

import org.example.finflowgateway.api.GatewayErrorCode;
import org.example.finflowgateway.api.GatewayException;
import org.example.finflowgateway.profile.OpenApiAccessProfile;
import org.example.finflowgateway.ratelimit.OpenApiEdgeGuard;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * M13 网关配额过滤器：接口编码映射、未知路径放行、策略错误透传。
 */
class OpenApiQuotaFilterTest {

    private final OpenApiEdgeGuard edgeGuard = mock(OpenApiEdgeGuard.class);
    private final OpenApiQuotaFilter filter = new OpenApiQuotaFilter(edgeGuard);
    private final OpenApiAccessProfile.QuotaPolicy policy =
            new OpenApiAccessProfile.QuotaPolicy("PAYMENT_CREATE", true, 20, 50_000L);

    @Test
    void apiCodeMapsEachOpenApiEndpoint() {
        assertEquals("PAYMENT_CREATE", OpenApiQuotaFilter.apiCode(HttpMethod.POST, "/open/v1/payments"));
        assertEquals("PAYMENT_CLOSE", OpenApiQuotaFilter.apiCode(HttpMethod.POST, "/open/v1/payments/P1/close"));
        assertEquals("PAYMENT_QUERY", OpenApiQuotaFilter.apiCode(HttpMethod.GET, "/open/v1/payments/P1"));
        assertEquals("REFUND_CREATE", OpenApiQuotaFilter.apiCode(HttpMethod.POST, "/open/v1/refunds"));
        assertEquals("REFUND_QUERY", OpenApiQuotaFilter.apiCode(HttpMethod.GET, "/open/v1/refunds/R1"));
        assertNull(OpenApiQuotaFilter.apiCode(HttpMethod.DELETE, "/open/v1/payments/P1"));
        assertNull(OpenApiQuotaFilter.apiCode(HttpMethod.GET, "/open/v1/unknown"));
    }

    @Test
    void missingProfileIsRejectedBeforeQuotaCheck() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("http://localhost/open/v1/payments").build());

        GatewayException error = assertThrows(GatewayException.class,
                () -> filter.filter(exchange, noOpChain()).block());

        assertEquals(GatewayErrorCode.SYSTEM_ERROR, error.getErrorCode());
        verifyNoInteractions(edgeGuard);
    }

    @Test
    void unknownPathPassesThroughWithoutQuota() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/open/v1/unknown").build());
        exchange.getAttributes().put(OpenApiAuthFilter.PROFILE_ATTRIBUTE, profile(policy));
        AtomicBoolean forwarded = new AtomicBoolean();
        GatewayFilterChain chain = forwardedExchange -> {
            forwarded.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(forwarded.get());
        verifyNoInteractions(edgeGuard);
    }

    @Test
    void knownPathChecksQuotaThenForwards() {
        when(edgeGuard.checkQuota(1L, "PAYMENT_CREATE", policy)).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("http://localhost/open/v1/payments").build());
        exchange.getAttributes().put(OpenApiAuthFilter.PROFILE_ATTRIBUTE, profile(policy));
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, forwardedExchange -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        verify(edgeGuard).checkQuota(1L, "PAYMENT_CREATE", policy);
        assertTrue(forwarded.get());
    }

    @Test
    void quotaRejectionPropagatesAndSkipsForwarding() {
        when(edgeGuard.checkQuota(1L, "PAYMENT_CREATE", policy)).thenReturn(
                Mono.error(new GatewayException(GatewayErrorCode.QUOTA_EXCEEDED, "超过限流")));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("http://localhost/open/v1/payments").build());
        exchange.getAttributes().put(OpenApiAuthFilter.PROFILE_ATTRIBUTE, profile(policy));
        AtomicBoolean forwarded = new AtomicBoolean();

        GatewayException error = assertThrows(GatewayException.class, () -> filter.filter(exchange,
                forwardedExchange -> Mono.defer(() -> {
                    forwarded.set(true);
                    return Mono.<Void>empty();
                })).block());

        assertEquals(GatewayErrorCode.QUOTA_EXCEEDED, error.getErrorCode());
        assertFalse(forwarded.get());
    }

    private OpenApiAccessProfile profile(OpenApiAccessProfile.QuotaPolicy quotaPolicy) {
        return new OpenApiAccessProfile(1L, "fk_test", "fs_test_secret", null, "ACTIVE", List.of(quotaPolicy));
    }

    private GatewayFilterChain noOpChain() {
        return exchange -> Mono.empty();
    }
}
