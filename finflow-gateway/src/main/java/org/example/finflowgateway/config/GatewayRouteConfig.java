package org.example.finflowgateway.config;

import org.example.finflowgateway.filter.OpenApiAuthFilter;
import org.example.finflowgateway.filter.OpenApiEnvelopeFilter;
import org.example.finflowgateway.filter.OpenApiQuotaFilter;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * M13 路由：只把 /open/v1/** 路由到 backend；后台 /api/v1/** 不在此处出现，
 * 保证「商户请求只经网关，后台请求不经网关」。
 * 过滤器按 route 挂载而不是注册成全局过滤器，后续新增后台路由不会误套商户鉴权。
 *
 * <p>M14：目标地址默认是 {@code lb://finflow-backend}，由网关的 ReactiveLoadBalancerClientFilter 解析；
 * 取注入档案的 WebClient 用 {@code @LoadBalanced} 构建器，同样按服务名寻址。</p>
 */
@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator openApiRoutes(RouteLocatorBuilder builder, GatewayProperties properties,
                                      OpenApiEnvelopeFilter envelopeFilter,
                                      OpenApiAuthFilter authFilter,
                                      OpenApiQuotaFilter quotaFilter) {
        return builder.routes()
                .route("merchant-openapi", route -> route
                        .path("/open/v1/**")
                        .filters(f -> f.filter(envelopeFilter).filter(authFilter).filter(quotaFilter))
                        .uri(properties.backendUri()))
                .build();
    }

    /**
     * 仅用于取 backend 注入档案（/api/v1/internal/openapi/**），与商户路由无关。
     * M15：手工 builder 不会继承 Boot 自动配置的 observation 能力，需显式挂 ObservationRegistry，
     * 档案拉取请求才会带 traceparent（否则这条 HTTP 出口会成为链路断点）。
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder openApiWebClientBuilder(ObservationRegistry observationRegistry) {
        return WebClient.builder().observationRegistry(observationRegistry);
    }

    @Bean
    public WebClient openApiWebClient(@LoadBalanced WebClient.Builder builder, GatewayProperties properties) {
        return builder.baseUrl(properties.backendUri()).build();
    }
}
