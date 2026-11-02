package org.example.finflowgateway.filter;

import org.example.finflowgateway.api.GatewayErrorCode;
import org.example.finflowgateway.api.GatewayException;
import org.example.finflowgateway.config.GatewayProperties;
import org.example.finflowgateway.config.OpenApiEdgeProperties;
import org.example.finflowgateway.profile.OpenApiAccessProfile;
import org.example.finflowgateway.profile.OpenApiProfileClient;
import org.example.finflowgateway.ratelimit.OpenApiEdgeGuard;
import org.example.finflowgateway.security.InternalHeaders;
import org.example.finflowgateway.security.OpenApiSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * M13 网关验签过滤器：拒绝路径逐条覆盖（缺头 / 凭证 / 商户状态 / 时间戳 / IP / 签名 / nonce），
 * 再验证通过后的可信头写入与请求体回放——下游 backend 必须能重新读到原始 body。
 */
class OpenApiAuthFilterTest {

    private static final String API_KEY = "fk_test";
    private static final String SECRET = "fs_test_secret";
    private static final String INTERNAL_TOKEN = "internal-token";
    private static final String PATH = "/open/v1/payments";
    private static final String BODY = "{\"merchantOrderNo\":\"T1\",\"amount\":100}";

    private final OpenApiProfileClient profileClient = mock(OpenApiProfileClient.class);
    private final OpenApiEdgeGuard edgeGuard = mock(OpenApiEdgeGuard.class);
    private final GatewayProperties properties =
            new GatewayProperties("http://127.0.0.1:8080", INTERNAL_TOKEN, 15, 2000);
    private final OpenApiEdgeProperties edgeProperties = new OpenApiEdgeProperties(300, 600);
    private final OpenApiAuthFilter filter =
            new OpenApiAuthFilter(profileClient, edgeGuard, properties, edgeProperties);

    @BeforeEach
    void stubHappyPath() {
        when(profileClient.find(API_KEY)).thenReturn(Mono.just(profile("ACTIVE", null)));
        when(edgeGuard.consumeNonce(anyString(), anyString())).thenReturn(Mono.just(true));
    }

    @Test
    void missingSignatureHeadersAreRejected() {
        GatewayException error = assertThrows(GatewayException.class,
                () -> filter.filter(exchange("POST", PATH, null, null, null, null, "203.0.113.5", BODY),
                        noOpChain()).block());

        assertEquals(GatewayErrorCode.AUTH_FAILED, error.getErrorCode());
        assertEquals("缺少签名请求头（X-Api-Key / X-Timestamp / X-Nonce / X-Signature）", error.getMessage());
    }

    @Test
    void unknownCredentialIsRejected() {
        when(profileClient.find(API_KEY)).thenReturn(Mono.empty());

        GatewayException error = assertThrows(GatewayException.class,
                () -> filter.filter(signedExchange(BODY, now(), "n1", null, "203.0.113.5"), noOpChain()).block());

        assertEquals(GatewayErrorCode.CREDENTIAL_INVALID, error.getErrorCode());
    }

    @Test
    void inactiveMerchantIsRejected() {
        when(profileClient.find(API_KEY)).thenReturn(Mono.just(profile("SUSPENDED", null)));

        GatewayException error = assertThrows(GatewayException.class,
                () -> filter.filter(signedExchange(BODY, now(), "n1", null, "203.0.113.5"), noOpChain()).block());

        assertEquals(GatewayErrorCode.MERCHANT_UNAVAILABLE, error.getErrorCode());
    }

    @Test
    void timestampOutsideWindowIsRejected() {
        GatewayException error = assertThrows(GatewayException.class,
                () -> filter.filter(signedExchange(BODY, now() - 3_600_000L, "n1", null, "203.0.113.5"),
                        noOpChain()).block());

        assertEquals(GatewayErrorCode.TIMESTAMP_EXPIRED, error.getErrorCode());
    }

    @Test
    void ipOutsideWhitelistIsRejected() {
        when(profileClient.find(API_KEY)).thenReturn(Mono.just(profile("ACTIVE", "198.51.100.0/24")));

        GatewayException error = assertThrows(GatewayException.class,
                () -> filter.filter(signedExchange(BODY, now(), "n1", null, "203.0.113.5"), noOpChain()).block());

        assertEquals(GatewayErrorCode.IP_NOT_ALLOWED, error.getErrorCode());
    }

    @Test
    void badSignatureIsRejected() {
        MockServerWebExchange exchange = exchange("POST", PATH,
                API_KEY, String.valueOf(now()), "n1", "0".repeat(64), "203.0.113.5", BODY);

        GatewayException error = assertThrows(GatewayException.class,
                () -> filter.filter(exchange, noOpChain()).block());

        assertEquals(GatewayErrorCode.AUTH_FAILED, error.getErrorCode());
    }

    @Test
    void replayedNonceIsRejected() {
        when(edgeGuard.consumeNonce(anyString(), anyString())).thenReturn(Mono.just(false));

        GatewayException error = assertThrows(GatewayException.class,
                () -> filter.filter(signedExchange(BODY, now(), "n1", null, "203.0.113.5"), noOpChain()).block());

        assertEquals(GatewayErrorCode.NONCE_REPLAYED, error.getErrorCode());
    }

    @Test
    void validRequestForwardsTrustedHeadersAndReplaysBody() {
        MockServerWebExchange exchange = signedExchange(BODY, now(), "n1", null, "203.0.113.5");
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = forwardedExchange -> {
            forwarded.set(forwardedExchange);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        ServerWebExchange result = forwarded.get();
        assertNotNull(result);
        assertEquals(INTERNAL_TOKEN, result.getRequest().getHeaders().getFirst(InternalHeaders.TOKEN));
        assertEquals("1", result.getRequest().getHeaders().getFirst(InternalHeaders.MERCHANT_ID));
        assertEquals(API_KEY, result.getRequest().getHeaders().getFirst(OpenApiSignature.HEADER_API_KEY));
        assertNotNull(result.getAttribute(OpenApiAuthFilter.PROFILE_ATTRIBUTE));

        DataBuffer buffer = DataBufferUtils.join(result.getRequest().getBody()).block();
        assertNotNull(buffer);
        byte[] replayed = new byte[buffer.readableByteCount()];
        buffer.read(replayed);
        DataBufferUtils.release(buffer);
        assertEquals(BODY, new String(replayed, StandardCharsets.UTF_8));
    }

    @Test
    void emptyBodyRequestForwardsWithoutBody() {
        MockServerWebExchange exchange = signedExchange("", now(), "n1", null, "203.0.113.5");
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = forwardedExchange -> {
            forwarded.set(forwardedExchange);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertNotNull(forwarded.get());
        String body = DataBufferUtils.join(forwarded.get().getRequest().getBody())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .defaultIfEmpty("")
                .block();
        assertEquals("", body);
    }

    private OpenApiAccessProfile profile(String status, String ipWhitelist) {
        return new OpenApiAccessProfile(1L, API_KEY, SECRET, ipWhitelist, status, List.of());
    }

    private GatewayFilterChain noOpChain() {
        return exchange -> Mono.empty();
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private MockServerWebExchange signedExchange(String body, long timestamp, String nonce,
                                                String apiKeyOverride, String remoteIp) {
        String key = apiKeyOverride == null ? API_KEY : apiKeyOverride;
        String canonical = OpenApiSignature.canonical("POST", PATH, null,
                body.getBytes(StandardCharsets.UTF_8), String.valueOf(timestamp), nonce);
        return exchange("POST", PATH, key, String.valueOf(timestamp), nonce, sign(canonical), remoteIp, body);
    }

    private MockServerWebExchange exchange(String method, String path, String apiKey, String timestamp,
                                          String nonce, String signature, String remoteIp, String body) {
        MockServerHttpRequest.BodyBuilder builder =
                MockServerHttpRequest.method(HttpMethod.valueOf(method), "http://localhost" + path);
        if (remoteIp != null) {
            builder.remoteAddress(new InetSocketAddress(remoteIp, 1234));
        }
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header(OpenApiSignature.HEADER_API_KEY, apiKey);
        }
        if (timestamp != null && !timestamp.isBlank()) {
            builder.header(OpenApiSignature.HEADER_TIMESTAMP, timestamp);
        }
        if (nonce != null && !nonce.isBlank()) {
            builder.header(OpenApiSignature.HEADER_NONCE, nonce);
        }
        if (signature != null && !signature.isBlank()) {
            builder.header(OpenApiSignature.HEADER_SIGNATURE, signature);
        }
        builder.contentType(MediaType.APPLICATION_JSON);
        // MockServerHttpRequest#body(String) 会立即构建请求，不能再走 builder.build()
        return MockServerWebExchange.from(body == null ? builder.build() : builder.body(body));
    }

    private String sign(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
