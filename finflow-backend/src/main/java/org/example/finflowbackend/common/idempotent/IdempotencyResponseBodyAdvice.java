package org.example.finflowbackend.common.idempotent;

import org.springframework.core.MethodParameter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * 幂等响应回填：{@link Idempotent} 接口的 2xx 响应序列化后写入 Redis，
 * 供 {@link IdempotencyInterceptor} 在 TTL 内回放。失败响应不缓存，重试可正常到达业务。
 */
@ControllerAdvice
public class IdempotencyResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyResponseBodyAdvice(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.hasMethodAnnotation(Idempotent.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)
                || !(response instanceof ServletServerHttpResponse servletResponse)) {
            return body;
        }
        IdempotencyInterceptor.Context context = (IdempotencyInterceptor.Context) servletRequest.getServletRequest()
                .getAttribute(IdempotencyInterceptor.ATTR_CONTEXT);
        if (context == null) {
            return body;
        }
        int status = servletResponse.getServletResponse().getStatus();
        if (status < 200 || status >= 300) {
            return body;
        }
        IdempotencyEntry entry = new IdempotencyEntry(context.fingerprint(), objectMapper.writeValueAsString(body));
        redisTemplate.opsForValue().set(context.redisKey(),
                objectMapper.writeValueAsString(entry), Duration.ofSeconds(context.ttlSeconds()));
        return body;
    }
}
