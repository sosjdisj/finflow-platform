package org.example.finflowbackend.common.idempotent;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.finflowbackend.common.web.CachedBodyRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 幂等前置过滤器：带 Idempotency-Key 的请求把 body 缓存进包装类，
 * 使 body 能被「指纹计算」与「@RequestBody 反序列化」各读一次。
 * 不带该头的请求原样放行，零开销。包装类与 OpenAPI 验签过滤器共用。
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getHeader(HEADER) != null && !(request instanceof CachedBodyRequest)) {
            chain.doFilter(new CachedBodyRequest(request), response);
            return;
        }
        chain.doFilter(request, response);
    }
}
