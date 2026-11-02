package org.example.finflowgateway.filter;

import org.example.finflowgateway.api.GatewayErrorCode;
import org.example.finflowgateway.api.GatewayException;
import org.example.finflowgateway.config.GatewayProperties;
import org.example.finflowgateway.config.OpenApiEdgeProperties;
import org.example.finflowgateway.profile.OpenApiAccessProfile;
import org.example.finflowgateway.profile.OpenApiProfileClient;
import org.example.finflowgateway.ratelimit.OpenApiEdgeGuard;
import org.example.finflowgateway.security.InternalHeaders;
import org.example.finflowgateway.security.IpWhitelist;
import org.example.finflowgateway.security.OpenApiSignature;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 网关 AK/SK 验签（M13）：/open/v1/** 的第一道门。
 *
 * <p>校验顺序与 backend 对齐：凭证存在且启用 → 商户生效 → 时间戳窗口 → IP 白名单 → 签名 → nonce。
 * 验签通过后把商户号与内部令牌写入请求头转发给 backend；请求体在网关缓存一次，
 * 既用于 sha256 参与签名，也供下游重新读取，不会出现「验签读空 body」。</p>
 */
@Component
public class OpenApiAuthFilter implements GatewayFilter, Ordered {

    /** 验签通过后写入 exchange attribute，配额过滤器与后续步骤直接复用，避免二次取档案。 */
    public static final String PROFILE_ATTRIBUTE = OpenApiAuthFilter.class.getName() + ".profile";

    private final OpenApiProfileClient profileClient;
    private final OpenApiEdgeGuard edgeGuard;
    private final GatewayProperties properties;
    private final OpenApiEdgeProperties edgeProperties;

    public OpenApiAuthFilter(OpenApiProfileClient profileClient, OpenApiEdgeGuard edgeGuard,
                             GatewayProperties properties, OpenApiEdgeProperties edgeProperties) {
        this.profileClient = profileClient;
        this.edgeGuard = edgeGuard;
        this.properties = properties;
        this.edgeProperties = edgeProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String apiKey = text(headers.getFirst(OpenApiSignature.HEADER_API_KEY));
        String timestamp = text(headers.getFirst(OpenApiSignature.HEADER_TIMESTAMP));
        String nonce = text(headers.getFirst(OpenApiSignature.HEADER_NONCE));
        String signature = text(headers.getFirst(OpenApiSignature.HEADER_SIGNATURE));
        if (apiKey == null || timestamp == null || nonce == null || signature == null) {
            return Mono.error(new GatewayException(GatewayErrorCode.AUTH_FAILED,
                    "缺少签名请求头（X-Api-Key / X-Timestamp / X-Nonce / X-Signature）"));
        }

        return profileClient.find(apiKey)
                .switchIfEmpty(Mono.error(new GatewayException(GatewayErrorCode.CREDENTIAL_INVALID)))
                .flatMap(profile -> authenticate(exchange, chain, profile, timestamp, nonce, signature));
    }

    private Mono<Void> authenticate(ServerWebExchange exchange, GatewayFilterChain chain,
                                    OpenApiAccessProfile profile, String timestamp, String nonce, String signature) {
        if (!"ACTIVE".equals(profile.merchantStatus())) {
            return Mono.error(new GatewayException(GatewayErrorCode.MERCHANT_UNAVAILABLE));
        }
        long requestTime = parseTimestamp(timestamp);
        if (!OpenApiSignature.timestampInWindow(requestTime, System.currentTimeMillis(),
                edgeProperties.timestampWindowSeconds())) {
            return Mono.error(new GatewayException(GatewayErrorCode.TIMESTAMP_EXPIRED));
        }
        if (!IpWhitelist.allows(profile.ipWhitelist(), clientIp(exchange))) {
            return Mono.error(new GatewayException(GatewayErrorCode.IP_NOT_ALLOWED));
        }

        return readableBody(exchange).flatMap(bodyBytes -> {
            String canonical = OpenApiSignature.canonical(exchange.getRequest().getMethod().name(),
                    exchange.getRequest().getURI().getRawPath(), exchange.getRequest().getURI().getRawQuery(),
                    bodyBytes, timestamp, nonce);
            if (!OpenApiSignature.matches(profile.secret(), canonical, signature)) {
                return Mono.error(new GatewayException(GatewayErrorCode.AUTH_FAILED));
            }
            return edgeGuard.consumeNonce(profile.apiKey(), nonce)
                    .flatMap(consumed -> Boolean.TRUE.equals(consumed)
                            ? forward(exchange, chain, profile, bodyBytes)
                            : Mono.error(new GatewayException(GatewayErrorCode.NONCE_REPLAYED)));
        });
    }

    private Mono<Void> forward(ServerWebExchange exchange, GatewayFilterChain chain,
                               OpenApiAccessProfile profile, byte[] bodyBytes) {
        exchange.getAttributes().put(PROFILE_ATTRIBUTE, profile);
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(InternalHeaders.TOKEN, properties.internalToken());
                    headers.set(InternalHeaders.MERCHANT_ID, String.valueOf(profile.merchantId()));
                    headers.set(OpenApiSignature.HEADER_API_KEY, profile.apiKey());
                })
                .build();
        ServerHttpRequest decorated = new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                if (bodyBytes.length == 0) {
                    return Flux.empty();
                }
                return Flux.defer(() -> Flux.just(exchange.getResponse().bufferFactory().wrap(bodyBytes)));
            }
        };
        return chain.filter(exchange.mutate().request(decorated).build());
    }

    /** 一次性读完请求体；空 body 按空字节数组处理，与签名规范里 sha256("") 对齐。 */
    private Mono<byte[]> readableBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .defaultIfEmpty(new byte[0]);
    }

    private long parseTimestamp(String timestamp) {
        try {
            return Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new GatewayException(GatewayErrorCode.AUTH_FAILED, "时间戳格式非法");
        }
    }

    private String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return null;
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Override
    public int getOrder() {
        return -90;
    }
}
