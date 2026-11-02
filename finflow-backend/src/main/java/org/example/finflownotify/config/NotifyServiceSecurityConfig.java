package org.example.finflownotify.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 通知服务安全策略（M16）：只对内网暴露管理端点与健康检查，其余一律拒绝。
 * 管理端点由 backend 经 Feign 调用；本服务不签发也不校验运营后台 JWT。
 */
@Configuration
@EnableWebSecurity
public class NotifyServiceSecurityConfig {

    @Bean
    public SecurityFilterChain notifyServiceFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Prometheus 抓取（M15）同样只在内网可达
                        .requestMatchers("/api/v1/internal/notify/**", "/actuator/health",
                                "/actuator/prometheus").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
