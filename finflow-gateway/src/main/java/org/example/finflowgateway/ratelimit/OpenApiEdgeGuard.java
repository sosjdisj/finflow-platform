package org.example.finflowgateway.ratelimit;

import org.example.finflowgateway.api.GatewayErrorCode;
import org.example.finflowgateway.api.GatewayException;
import org.example.finflowgateway.config.GatewayProperties;
import org.example.finflowgateway.config.OpenApiEdgeProperties;
import org.example.finflowgateway.profile.OpenApiAccessProfile;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 边缘准入存储（M13）：nonce 防重放 + 配额计数，全部落 Redis。
 *
 * <p>配额是「边缘限流」：用 openapi:edge:quota:* 独立键位，和 backend 的 openapi:quota:* 各计一套，
 * 同一份阈值、两层都生效；网关故障或回退时 backend 仍能独立兜住，不会因为共用键位把额度算成一半。
 * Redis 异常不吞、按系统错误拒绝，避免限流失效却静默放行。</p>
 */
@Component
public class OpenApiEdgeGuard {

    private static final String NONCE_KEY_PREFIX = "openapi:nonce:";
    private static final String QPS_KEY_PREFIX = "openapi:edge:quota:qps:";
    private static final String DAILY_KEY_PREFIX = "openapi:edge:quota:daily:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final OpenApiEdgeProperties edgeProperties;

    public OpenApiEdgeGuard(ReactiveStringRedisTemplate redisTemplate, OpenApiEdgeProperties edgeProperties) {
        this.redisTemplate = redisTemplate;
        this.edgeProperties = edgeProperties;
    }

    /** 首次使用返回 true；窗口内重复使用返回 false。与 backend 共用同一键位，跨入口也能防重放。 */
    public Mono<Boolean> consumeNonce(String apiKey, String nonce) {
        return redisTemplate.opsForValue()
                .setIfAbsent(NONCE_KEY_PREFIX + apiKey + ":" + nonce, "1",
                        Duration.ofSeconds(edgeProperties.nonceTtlSeconds()));
    }

    /** 停用 → 47009；QPS / 日配额超限 → 47008；policy 为空（接口未登记）不限制。 */
    public Mono<Void> checkQuota(Long merchantId, String apiCode, OpenApiAccessProfile.QuotaPolicy policy) {
        if (policy == null) {
            return Mono.empty();
        }
        if (!policy.enabled()) {
            return Mono.error(new GatewayException(GatewayErrorCode.OPENAPI_DISABLED,
                    "接口 " + apiCode + " 已停用"));
        }
        long second = System.currentTimeMillis() / 1000;
        String qpsKey = QPS_KEY_PREFIX + merchantId + ":" + apiCode + ":" + second;
        String dailyKey = DAILY_KEY_PREFIX + merchantId + ":" + apiCode + ":" + LocalDate.now();
        return increment(qpsKey, Duration.ofSeconds(2))
                .flatMap(count -> count.longValue() > policy.effectiveQpsLimit()
                        ? Mono.<Long>error(new GatewayException(GatewayErrorCode.QUOTA_EXCEEDED,
                        "接口 " + apiCode + " 超过每秒 " + policy.effectiveQpsLimit() + " 次限流"))
                        : increment(dailyKey, untilTomorrow()))
                .flatMap(count -> count.longValue() > policy.effectiveDailyLimit()
                        ? Mono.<Void>error(new GatewayException(GatewayErrorCode.QUOTA_EXCEEDED,
                        "接口 " + apiCode + " 超过每日 " + policy.effectiveDailyLimit() + " 次配额"))
                        : Mono.<Void>empty());
    }

    /** INCR 并在首次自增时设置过期；key 意外丢失后下一轮重新计数，与 backend 行为一致。 */
    private Mono<Long> increment(String key, Duration ttl) {
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> count != null && count == 1L
                        ? redisTemplate.expire(key, ttl).thenReturn(count)
                        : Mono.just(count == null ? 0L : count));
    }

    private Duration untilTomorrow() {
        LocalDateTime resetAt = LocalDate.now().plusDays(1).atStartOfDay().plusMinutes(5);
        long seconds = Duration.between(LocalDateTime.now(), resetAt).getSeconds();
        return Duration.ofSeconds(Math.max(seconds, 60));
    }
}
