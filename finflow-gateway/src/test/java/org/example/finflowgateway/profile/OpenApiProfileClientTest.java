package org.example.finflowgateway.profile;

import org.example.finflowgateway.api.GatewayErrorCode;
import org.example.finflowgateway.api.GatewayException;
import org.example.finflowgateway.config.GatewayProperties;
import org.example.finflowgateway.security.InternalHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * M13 网关注入档案客户端：信封解析、404 视为凭证无效、5xx/业务码异常统一按系统错误、
 * 短期缓存避免每个请求都打 backend。
 */
class OpenApiProfileClientTest {

    private static final String API_KEY = "fk_test";
    private static final String INTERNAL_TOKEN = "internal-token";
    private static final String PROFILE_JSON = """
            {"code":0,"message":"ok","data":{"merchantId":1,"apiKey":"fk_test","secret":"fs_test_secret",
            "ipWhitelist":"10.0.0.0/8","merchantStatus":"ACTIVE",
            "quotas":[{"apiCode":"PAYMENT_CREATE","enabled":true,"effectiveQpsLimit":20,"effectiveDailyLimit":50000}]}}
            """.replace("\n", "");

    private final GatewayProperties properties =
            new GatewayProperties("http://127.0.0.1:8080", INTERNAL_TOKEN, 15, 2000);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<ClientRequest> lastRequest = new AtomicReference<>();

    @Test
    void parsesProfileAndUsesInternalToken() {
        OpenApiProfileClient client = client(HttpStatus.OK, PROFILE_JSON);

        OpenApiAccessProfile profile = client.find(API_KEY).block();

        assertNotNull(profile);
        assertEquals(1L, profile.merchantId());
        assertEquals("fs_test_secret", profile.secret());
        assertEquals("10.0.0.0/8", profile.ipWhitelist());
        assertEquals("ACTIVE", profile.merchantStatus());
        assertEquals(1, profile.quotas().size());
        assertEquals(20, profile.quotas().getFirst().effectiveQpsLimit());
        assertEquals(50_000L, profile.quotas().getFirst().effectiveDailyLimit());

        assertEquals(INTERNAL_TOKEN, lastRequest.get().headers().getFirst(InternalHeaders.TOKEN));
        assertEquals("/api/v1/internal/openapi/profiles/" + API_KEY, lastRequest.get().url().getPath());
    }

    @Test
    void missingProfileReturnsEmpty() {
        OpenApiProfileClient client = client(HttpStatus.NOT_FOUND, "");

        OpenApiAccessProfile profile = client.find(API_KEY).block();

        assertNull(profile);
        assertEquals(1, calls.get());
    }

    @Test
    void serverErrorIsRejectedAsSystemError() {
        OpenApiProfileClient client = client(HttpStatus.INTERNAL_SERVER_ERROR, "");

        GatewayException error = assertThrows(GatewayException.class, () -> client.find(API_KEY).block());

        assertEquals(GatewayErrorCode.SYSTEM_ERROR, error.getErrorCode());
        assertEquals("取注入档案失败: HTTP 500", error.getMessage());
    }

    @Test
    void nonZeroEnvelopeCodeIsRejectedAsSystemError() {
        OpenApiProfileClient client = client(HttpStatus.OK,
                "{\"code\":47005,\"message\":\"API 凭证无效或已停用\",\"data\":null}");

        GatewayException error = assertThrows(GatewayException.class, () -> client.find(API_KEY).block());

        assertEquals(GatewayErrorCode.SYSTEM_ERROR, error.getErrorCode());
        assertEquals("取注入档案失败: API 凭证无效或已停用", error.getMessage());
    }

    @Test
    void cachedProfileIsReusedWithinTtl() {
        OpenApiProfileClient client = client(HttpStatus.OK, PROFILE_JSON);

        client.find(API_KEY).block();
        client.find(API_KEY).block();
        client.find(API_KEY).block();

        assertEquals(1, calls.get());
    }

    private OpenApiProfileClient client(HttpStatusCode status, String body) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    calls.incrementAndGet();
                    lastRequest.set(request);
                    return reactor.core.publisher.Mono.just(ClientResponse.create(status)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(body)
                            .build());
                })
                .codecs(configurer -> configurer.defaultCodecs()
                        .jacksonJsonDecoder(new JacksonJsonDecoder(JsonMapper.builder().build())))
                .build();
        return new OpenApiProfileClient(webClient, properties);
    }
}
