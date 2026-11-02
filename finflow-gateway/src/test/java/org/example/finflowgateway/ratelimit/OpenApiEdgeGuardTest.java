package org.example.finflowgateway.ratelimit;

import org.example.finflowgateway.api.GatewayErrorCode;
import org.example.finflowgateway.api.GatewayException;
import org.example.finflowgateway.config.OpenApiEdgeProperties;
import org.example.finflowgateway.profile.OpenApiAccessProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M13 边缘配额：停用 / QPS / 日配额三条拒绝路径与正常放行。
 * Redis 用 mock 模拟计数，阈值判断是本类职责，不依赖真实 Redis。
 */
class OpenApiEdgeGuardTest {

    private final ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);
    private final OpenApiEdgeProperties edgeProperties = new OpenApiEdgeProperties(300, 600);
    private final OpenApiEdgeGuard guard = new OpenApiEdgeGuard(redisTemplate, edgeProperties);

    @BeforeEach
    void stubRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
    }

    @Test
    void disabledApiIsRejectedWithoutCounting() {
        GatewayException error = assertThrows(GatewayException.class,
                () -> guard.checkQuota(1L, "PAYMENT_CREATE", policy(false, 20, 50_000L)).block());

        assertEquals(GatewayErrorCode.OPENAPI_DISABLED, error.getErrorCode());
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void qpsExceededIsRejected() {
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(21L));

        GatewayException error = assertThrows(GatewayException.class,
                () -> guard.checkQuota(1L, "PAYMENT_CREATE", policy(true, 20, 50_000L)).block());

        assertEquals(GatewayErrorCode.QUOTA_EXCEEDED, error.getErrorCode());
        assertEquals("接口 PAYMENT_CREATE 超过每秒 20 次限流", error.getMessage());
    }

    @Test
    void dailyExceededIsRejected() {
        when(valueOperations.increment(anyString()))
                .thenReturn(Mono.just(1L))
                .thenReturn(Mono.just(50_001L));

        GatewayException error = assertThrows(GatewayException.class,
                () -> guard.checkQuota(1L, "PAYMENT_CREATE", policy(true, 20, 50_000L)).block());

        assertEquals(GatewayErrorCode.QUOTA_EXCEEDED, error.getErrorCode());
        assertEquals("接口 PAYMENT_CREATE 超过每日 50000 次配额", error.getMessage());
    }

    @Test
    void withinLimitsPassesAndSetsTtlOnFirstIncrement() {
        when(valueOperations.increment(anyString()))
                .thenReturn(Mono.just(1L))
                .thenReturn(Mono.just(1L));

        guard.checkQuota(1L, "PAYMENT_CREATE", policy(true, 20, 50_000L)).block();

        verify(redisTemplate, times(2)).expire(anyString(), any(Duration.class));
    }

    @Test
    void unknownPolicyMeansNoLimit() {
        guard.checkQuota(1L, "PAYMENT_CREATE", null).block();

        Mockito.verifyNoInteractions(valueOperations);
    }

    @Test
    void nonceFirstUseIsAcceptedAndReplayIsRejected() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true))
                .thenReturn(Mono.just(false));

        boolean first = Boolean.TRUE.equals(guard.consumeNonce("fk_test", "n1").block());
        boolean replay = Boolean.TRUE.equals(guard.consumeNonce("fk_test", "n1").block());

        assertEquals(true, first);
        assertEquals(false, replay);
    }

    private OpenApiAccessProfile.QuotaPolicy policy(boolean enabled, int qps, long daily) {
        return new OpenApiAccessProfile.QuotaPolicy("PAYMENT_CREATE", enabled, qps, daily);
    }
}
