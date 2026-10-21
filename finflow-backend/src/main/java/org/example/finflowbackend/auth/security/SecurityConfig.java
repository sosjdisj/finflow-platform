package org.example.finflowbackend.auth.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.ErrorCode;
import org.example.finflowbackend.common.api.ErrorCodeSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 配置：无状态 JWT 过滤链 + 方法级权限注解（@PreAuthorize）。
 * 401 / 403 一律输出统一响应结构（code / message / data）。
 * 认证信息由 {@link JwtAuthenticationFilter} 写入 SecurityContext，
 * 权限点编码与 sys_permission.code 一致，业务代码通过 CurrentUserHolder 取当前登录主体。
 * 跨域不在此处理：开发走 vite 代理、生产走 Nginx 反代，均为同源。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** 无需登录即可访问的接口。S40 起 Swagger 与 Actuator 指标端点一并放行（prod 配置已关 Swagger）。 */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/health",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/captcha",
            // 渠道异步通知（通道侧回调，无登录态，靠 HMAC 签名验签）
            "/api/v1/channels/**",
            // S40 OpenAPI 文档
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            // S40 可观测性：健康检查 + Prometheus 抓取
            "/actuator/health",
            "/actuator/prometheus",
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, ErrorCode.FORBIDDEN)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 鉴权失败统一输出 ApiResponse JSON，避免返回 Security 默认报文。 */
    private void writeError(HttpServletResponse response, ErrorCodeSpec errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(errorCode)));
    }
}
