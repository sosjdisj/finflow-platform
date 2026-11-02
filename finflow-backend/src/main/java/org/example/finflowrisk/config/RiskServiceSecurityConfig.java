package org.example.finflowrisk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 风控服务安全策略（M11）：只对内网暴露决策端点与健康检查，其余一律拒绝。
 * 决策端点由 backend 经 Feign 调用；本服务不签发也不校验运营后台 JWT。
 */
@Configuration
@EnableWebSecurity
public class RiskServiceSecurityConfig {

    @Bean
    public SecurityFilterChain riskServiceFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Prometheus 抓取（M15）同样只在内网可达
                        .requestMatchers("/api/v1/internal/risk/**", "/actuator/health",
                                "/actuator/prometheus").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
