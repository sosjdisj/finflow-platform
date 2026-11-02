package org.example.finflowgateway.filter;

import org.example.finflowgateway.api.GatewayErrorCode;
import org.example.finflowgateway.api.GatewayException;
import org.example.finflowgateway.profile.OpenApiAccessProfile;
import org.example.finflowgateway.ratelimit.OpenApiEdgeGuard;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关配额校验（M13）：按「商户 + 接口编码」限流，策略来自验签阶段取回的注入档案。
 *
 * <p>接口编码由 method + path 推导，不读请求体里的业务字段；未知路径不在这里拦，
 * 交给 backend 返回统一 404（网关不重复实现路由语义）。</p>
 */
@Component
public class OpenApiQuotaFilter implements GatewayFilter, Ordered {

    private final OpenApiEdgeGuard edgeGuard;

    public OpenApiQuotaFilter(OpenApiEdgeGuard edgeGuard) {
        this.edgeGuard = edgeGuard;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        OpenApiAccessProfile profile = exchange.getAttribute(OpenApiAuthFilter.PROFILE_ATTRIBUTE);
        if (profile == null) {
            return Mono.error(new GatewayException(GatewayErrorCode.SYSTEM_ERROR));
        }
        String apiCode = apiCode(exchange.getRequest().getMethod(), exchange.getRequest().getURI().getRawPath());
        if (apiCode == null) {
            return chain.filter(exchange);
        }
        OpenApiAccessProfile.QuotaPolicy policy = profile.quotas() == null ? null
                : profile.quotas().stream().filter(q -> apiCode.equals(q.apiCode())).findFirst().orElse(null);
        return edgeGuard.checkQuota(profile.merchantId(), apiCode, policy).then(chain.filter(exchange));
    }

    /** 与 backend 的接口清单一一对应：新增接口时两处都要加（协议的一部分）。 */
    static String apiCode(HttpMethod method, String path) {
        if (method == HttpMethod.POST && "/open/v1/payments".equals(path)) {
            return "PAYMENT_CREATE";
        }
        if (method == HttpMethod.POST && path.startsWith("/open/v1/payments/") && path.endsWith("/close")) {
            return "PAYMENT_CLOSE";
        }
        if (method == HttpMethod.GET && path.startsWith("/open/v1/payments/")) {
            return "PAYMENT_QUERY";
        }
        if (method == HttpMethod.POST && "/open/v1/refunds".equals(path)) {
            return "REFUND_CREATE";
        }
        if (method == HttpMethod.GET && path.startsWith("/open/v1/refunds/")) {
            return "REFUND_QUERY";
        }
        return null;
    }

    @Override
    public int getOrder() {
        return -80;
    }
}
