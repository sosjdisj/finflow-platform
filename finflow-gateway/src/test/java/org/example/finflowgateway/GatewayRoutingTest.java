package org.example.finflowgateway;

import com.sun.net.httpserver.HttpServer;
import org.example.finflowgateway.profile.OpenApiAccessProfile;
import org.example.finflowgateway.profile.OpenApiProfileClient;
import org.example.finflowgateway.ratelimit.OpenApiEdgeGuard;
import org.example.finflowgateway.security.InternalHeaders;
import org.example.finflowgateway.security.OpenApiSignature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * M13 路由集成测试：真实启动网关，backend 用 JDK HttpServer 桩代替。
 * 验证「/open/v1/** 只经网关」「验签后带内部令牌与商户号」「后台路径不经网关」「统一错误与版本头」。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.sentinel.eager=false"
})
class GatewayRoutingTest {

    private static final String API_KEY = "fk_test";
    private static final String SECRET = "fs_test_secret";
    private static final String INTERNAL_TOKEN = "gateway-routing-token";
    private static final List<CapturedRequest> RECEIVED = new CopyOnWriteArrayList<>();
    private static final HttpServer BACKEND = startBackend();

    @LocalServerPort
    private int port;

    @MockitoBean
    private OpenApiProfileClient profileClient;

    @MockitoBean
    private OpenApiEdgeGuard edgeGuard;

    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("finflow.gateway.backend-uri", () -> "http://127.0.0.1:" + BACKEND.getAddress().getPort());
        registry.add("finflow.gateway.internal-token", () -> INTERNAL_TOKEN);
    }

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .build();
        RECEIVED.clear();
        when(profileClient.find(API_KEY)).thenReturn(Mono.just(new OpenApiAccessProfile(
                1L, API_KEY, SECRET, null, "ACTIVE", List.of())));
        when(edgeGuard.consumeNonce(anyString(), anyString())).thenReturn(Mono.just(true));
        when(edgeGuard.checkQuota(anyLong(), anyString(), any())).thenReturn(Mono.empty());
    }

    @AfterAll
    static void stopBackend() {
        BACKEND.stop(0);
    }

    @Test
    void signedRequestIsRoutedWithTrustedHeadersAndTraceId() {
        String body = "{\"merchantOrderNo\":\"GT-1\",\"amount\":10000,\"subject\":\"网关路由\",\"paymentMethod\":\"ALIPAY\"}";
        long timestamp = System.currentTimeMillis();
        String nonce = "n-route";

        webTestClient.post().uri("/open/v1/payments")
                .header(OpenApiSignature.HEADER_API_KEY, API_KEY)
                .header(OpenApiSignature.HEADER_TIMESTAMP, String.valueOf(timestamp))
                .header(OpenApiSignature.HEADER_NONCE, nonce)
                .header(OpenApiSignature.HEADER_SIGNATURE, sign("POST", "/open/v1/payments", body, timestamp, nonce))
                .header("X-Trace-Id", "route-trace")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-OpenApi-Version", "v1")
                .expectHeader().valueEquals("X-Trace-Id", "route-trace")
                .expectBody().jsonPath("$.code").isEqualTo(0);

        assertEquals(1, RECEIVED.size());
        CapturedRequest request = RECEIVED.getFirst();
        assertEquals(INTERNAL_TOKEN, request.internalToken());
        assertEquals("1", request.merchantId());
        assertEquals(API_KEY, request.apiKey());
        assertEquals("route-trace", request.traceId());
        assertEquals("/open/v1/payments", request.path());
        assertEquals(body, request.body());
    }

    @Test
    void unsignedRequestIsRejectedBeforeReachingBackend() {
        webTestClient.post().uri("/open/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-OpenApi-Version", "v1")
                .expectBody().jsonPath("$.code").isEqualTo(47002);

        assertTrue(RECEIVED.isEmpty());
    }

    @Test
    void adminPathIsNotExposedThroughGateway() {
        webTestClient.get().uri("/api/v1/merchants")
                .exchange()
                .expectStatus().isNotFound();

        assertTrue(RECEIVED.isEmpty());
    }

    private String sign(String method, String path, String body, long timestamp, String nonce) {
        String canonical = OpenApiSignature.canonical(method, path, null,
                body.getBytes(StandardCharsets.UTF_8), String.valueOf(timestamp), nonce);
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

    private static HttpServer startBackend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                RECEIVED.add(new CapturedRequest(
                        exchange.getRequestHeaders().getFirst(InternalHeaders.TOKEN),
                        exchange.getRequestHeaders().getFirst(InternalHeaders.MERCHANT_ID),
                        exchange.getRequestHeaders().getFirst(OpenApiSignature.HEADER_API_KEY),
                        exchange.getRequestHeaders().getFirst("X-Trace-Id"),
                        exchange.getRequestURI().getPath(),
                        body));
                byte[] response = "{\"code\":0,\"message\":\"ok\",\"data\":{\"platformOrderNo\":\"P1\"}}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(response);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("启动 backend 桩失败", e);
        }
    }

    private record CapturedRequest(String internalToken, String merchantId, String apiKey,
                                   String traceId, String path, String body) {
    }
}
