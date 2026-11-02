package org.example.finflowgateway.profile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.finflowgateway.api.GatewayErrorCode;
import org.example.finflowgateway.api.GatewayException;
import org.example.finflowgateway.config.GatewayProperties;
import org.example.finflowgateway.security.InternalHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * 网关注入档案客户端（M13）：backend 的凭证 / 商户状态 / 配额策略一次取回，短期缓存。
 *
 * <p>缓存只影响接入层的凭证解析速度，不承担状态权威——backend 收到可信请求仍会复核商户 ACTIVE，
 * 所以停用商户最迟在一个请求内生效。取档案失败（超时 / 5xx）按系统错误拒绝，绝不静默放行。</p>
 */
@Component
public class OpenApiProfileClient {

    private final WebClient webClient;
    private final GatewayProperties properties;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public OpenApiProfileClient(WebClient openApiWebClient, GatewayProperties properties) {
        this.webClient = openApiWebClient;
        this.properties = properties;
    }

    /** 凭证不存在 / 已停用返回 empty；backend 不可用时抛系统错误。 */
    public Mono<OpenApiAccessProfile> find(String apiKey) {
        CacheEntry cached = cache.get(apiKey);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMillis() > now) {
            return Mono.just(cached.profile());
        }
        long expiresAt = now + properties.profileCacheSeconds() * 1000L;
        return fetch(apiKey)
                .doOnNext(profile -> cache.put(apiKey, new CacheEntry(profile, expiresAt)));
    }

    private Mono<OpenApiAccessProfile> fetch(String apiKey) {
        return webClient.get()
                .uri("/api/v1/internal/openapi/profiles/{apiKey}", apiKey)
                .header(InternalHeaders.TOKEN, properties.internalToken())
                .exchangeToMono(response -> {
                    int status = response.statusCode().value();
                    if (status == 404) {
                        return response.releaseBody().then(Mono.empty());
                    }
                    if (status < 200 || status >= 300) {
                        return response.releaseBody().then(Mono.error(new GatewayException(
                                GatewayErrorCode.SYSTEM_ERROR, "取注入档案失败: HTTP " + status)));
                    }
                    return response.bodyToMono(ProfileEnvelope.class)
                            .flatMap(envelope -> envelope.data() != null && envelope.code() == 0
                                    ? Mono.just(envelope.data())
                                    : Mono.error(new GatewayException(GatewayErrorCode.SYSTEM_ERROR,
                                    "取注入档案失败: " + envelope.message())));
                })
                .timeout(Duration.ofMillis(properties.profileTimeoutMillis()))
                .onErrorMap(TimeoutException.class,
                        e -> new GatewayException(GatewayErrorCode.SYSTEM_ERROR, "取注入档案超时"))
                .onErrorMap(e -> !(e instanceof GatewayException),
                        e -> new GatewayException(GatewayErrorCode.SYSTEM_ERROR, "取注入档案失败"));
    }

    private record CacheEntry(OpenApiAccessProfile profile, long expiresAtMillis) {
    }

    /** backend ApiResponse 信封，只取 code 与 data。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProfileEnvelope(int code, String message, OpenApiAccessProfile data) {
    }
}
