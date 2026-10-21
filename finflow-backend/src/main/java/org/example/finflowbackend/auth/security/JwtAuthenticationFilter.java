package org.example.finflowbackend.auth.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.finflowbackend.auth.token.CurrentUser;
import org.example.finflowbackend.auth.token.JwtTokenProvider;
import org.example.finflowbackend.system.service.RbacAuthorityService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：解析 Bearer access token，加载用户权限编码并写入 SecurityContext。
 * 无凭证或凭证无效时按匿名放行，受保护接口由认证入口点统一回 401；
 * 权限不足由方法级注解（@PreAuthorize）裁决，回 403。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final RbacAuthorityService rbacAuthorityService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, RbacAuthorityService rbacAuthorityService) {
        this.tokenProvider = tokenProvider;
        this.rbacAuthorityService = rbacAuthorityService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                CurrentUser user = tokenProvider.parseAccessToken(header.substring(BEARER_PREFIX.length()));
                List<SimpleGrantedAuthority> authorities = rbacAuthorityService
                        .loadPermissionCodes(user.userId())
                        .stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, authorities));
            } catch (JwtException | IllegalArgumentException e) {
                // 无效凭证按匿名处理，走统一 401 入口点
            }
        }
        chain.doFilter(request, response);
    }
}
