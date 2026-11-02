package org.example.finflowbackend.common.config;

import org.example.finflowbackend.common.idempotent.IdempotencyInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC 公共配置：注册横切拦截器（幂等）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final IdempotencyInterceptor idempotencyInterceptor;

    public WebMvcConfig(IdempotencyInterceptor idempotencyInterceptor) {
        this.idempotencyInterceptor = idempotencyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // /open/v1 同样受 @Idempotent 约束（M12：商户下单 / 退款重复提交回放）
        registry.addInterceptor(idempotencyInterceptor).addPathPatterns("/api/v1/**", "/open/v1/**");
    }
}
