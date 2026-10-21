package org.example.finflowbackend.common.idempotent;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.finflowbackend.common.api.ErrorCode;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.common.util.HmacSigner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * API 幂等拦截器：作用于标注 {@link Idempotent} 的接口方法。
 * 首次请求以 SETNX 抢占 Redis 键（记录指纹、响应留空），成功后业务正常执行，
 * 响应由 {@link IdempotencyResponseBodyAdvice} 回填；TTL 内相同键重复请求：
 * 指纹不同 → 409 冲突；响应未就绪 → 409 处理中；否则原样回放首次响应。
 */
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    /** preHandle 与 ResponseBodyAdvice 之间传递幂等上下文的 request attribute 键。 */
    public static final String ATTR_CONTEXT = IdempotencyInterceptor.class.getName() + ".context";

    private static final String KEY_PREFIX = "idempotency:";
    private static final String NOT_JSON = "-";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyInterceptor(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 幂等上下文：Redis 键、请求指纹、TTL 秒数。 */
    public record Context(String redisKey, String fingerprint, long ttlSeconds) {
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Idempotent idempotent = handlerMethod.getMethodAnnotation(Idempotent.class);
        if (idempotent == null) {
            return true;
        }

        String key = request.getHeader(IdempotencyFilter.HEADER);
        if (key == null || key.isBlank()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_MISSING);
        }

        String fingerprint = fingerprint(request);
        String redisKey = KEY_PREFIX + request.getMethod() + ":" + request.getRequestURI() + ":" + key;
        long ttlSeconds = idempotent.ttlSeconds();

        String payload = objectMapper.writeValueAsString(new IdempotencyEntry(fingerprint, null));
        if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(redisKey, payload, Duration.ofSeconds(ttlSeconds)))) {
            request.setAttribute(ATTR_CONTEXT, new Context(redisKey, fingerprint, ttlSeconds));
            return true;
        }

        IdempotencyEntry existing = readEntry(redisKey);
        if (existing == null) {
            // SETNX 失败到 GET 之间键恰好过期（概率极低），按处理中处理，客户端重试即可
            throw new BusinessException(ErrorCode.IDEMPOTENCY_PROCESSING);
        }
        if (!fingerprint.equals(existing.fingerprint())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_MISMATCH);
        }
        if (existing.responseBody() == null) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_PROCESSING);
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(existing.responseBody());
        return false;
    }

    /** 请求指纹：SHA-256(body)；无 body 请求以固定串参与，指纹同样稳定。 */
    private String fingerprint(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        String content = body.length == 0 ? NOT_JSON : new String(body, StandardCharsets.UTF_8);
        return HmacSigner.sha256Hex(content);
    }

    private IdempotencyEntry readEntry(String redisKey) {
        String json = redisTemplate.opsForValue().get(redisKey);
        return json == null ? null : objectMapper.readValue(json, IdempotencyEntry.class);
    }
}
