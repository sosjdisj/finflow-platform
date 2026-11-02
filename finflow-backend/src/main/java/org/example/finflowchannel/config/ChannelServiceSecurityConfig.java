package org.example.finflowchannel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 渠道服务安全策略（M08）：服务只对内网提供两类端点，其余一律拒绝。
 * 内部操作端点由 backend 经 Feign 调用；渠道通知端点由渠道侧回调，靠 HMAC 验签而非登录态。
 * 不使用 backend 的 JWT 过滤链：本服务不签发也不校验运营后台令牌。
 */
@Configuration
@EnableWebSecurity
public class ChannelServiceSecurityConfig {

    @Bean
    public SecurityFilterChain channelServiceFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // backend 内部调用（M08）+ 渠道异步通知 + 健康检查 + Prometheus 抓取（M15）
                        .requestMatchers("/api/v1/internal/channel/**", "/api/v1/channels/**",
                                "/actuator/health", "/actuator/prometheus").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
