package org.example.finflowbackend.openapi.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * nonce 防重放存储（M12）：Redis SETNX 抢占，TTL 覆盖时间戳窗口。
 * 只在校验全部通过后写入，避免无效请求污染 Redis。
 */
@Component
public class OpenApiNonceStore {

    private static final String KEY_PREFIX = "openapi:nonce:";

    private final StringRedisTemplate redisTemplate;

    public OpenApiNonceStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 首次使用返回 true；窗口内重复使用返回 false。 */
    public boolean tryConsume(String apiKey, String nonce, int ttlSeconds) {
        Boolean created = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + apiKey + ":" + nonce, "1", Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(created);
    }
}
