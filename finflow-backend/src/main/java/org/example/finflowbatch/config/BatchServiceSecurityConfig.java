package org.example.finflowbatch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 批处理服务安全策略（M17）：本进程不对外提供业务端点，只保留健康检查与 Prometheus 抓取，
 * 其余一律拒绝；对账执行入口是 backend 的运营接口与夜间定时任务，不经过本进程。
 */
@Configuration
@EnableWebSecurity
public class BatchServiceSecurityConfig {

    @Bean
    public SecurityFilterChain batchServiceFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }
}
