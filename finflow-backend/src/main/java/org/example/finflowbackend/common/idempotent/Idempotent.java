package org.example.finflowbackend.common.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 幂等注解：客户端携带 Idempotency-Key 请求头后，
 * 相同键 + 相同请求体在 TTL 内重复请求将直接回放首次响应。
 * ttlSeconds 只约束「键 → 首次响应」的缓存窗口，按业务重试节奏设置。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** 幂等键缓存时长（秒）。 */
    int ttlSeconds() default 300;
}
