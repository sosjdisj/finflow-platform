package org.example.finflowbackend.webhook.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.sun.net.httpserver.HttpServer;
import org.example.finflowbackend.common.util.HmacSigner;
import org.example.finflowbackend.merchant.dto.MerchantDetailResponse;
import org.example.finflowbackend.merchant.dto.MerchantResponse;
import org.example.finflowbackend.merchant.entity.Merchant;
import org.example.finflowbackend.merchant.service.ApiKeyService;
import org.example.finflowbackend.merchant.service.MerchantService;
import org.example.finflowbackend.system.config.ConfigKeys;
import org.example.finflowbackend.system.service.ConfigService;
import org.example.finflowbackend.webhook.entity.WebhookDelivery;
import org.example.finflowbackend.webhook.entity.WebhookEvent;
import org.example.finflowbackend.webhook.mapper.WebhookDeliveryMapper;
import org.example.finflowbackend.webhook.mapper.WebhookEventMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Webhook 投递单测（S32 验收核心）：本地起 JDK 接收服务，
 * 验证签名 = HMAC-SHA256(timestamp + "." + body, secret)、报文与库中 payload 一致、
 * 投递日志一次尝试一行、失败按指数退避推进、耗尽与无订阅降级。
 */
class WebhookDeliveryServiceTest {

    private static final String SECRET = "fs_test_secret";

    private final WebhookEventMapper eventMapper = mock(WebhookEventMapper.class);
    private final WebhookDeliveryMapper deliveryMapper = mock(WebhookDeliveryMapper.class);
    private final MerchantService merchantService = mock(MerchantService.class);
    private final ApiKeyService apiKeyService = mock(ApiKeyService.class);
    private final ConfigService configService = mock(ConfigService.class);

    private WebhookDeliveryService service;
    private HttpServer server;
    private int port;

    /** 接收服务记录的请求（headers + body）与应答码。 */
    private final List<CapturedRequest> captured = new CopyOnWriteArrayList<>();
    private volatile int respondCode = 200;

    @BeforeEach
    void setUp() throws IOException {
        when(configService.getLong(ConfigKeys.WEBHOOK_MAX_RETRIES)).thenReturn(5L);
        service = new WebhookDeliveryService(eventMapper, deliveryMapper, merchantService, apiKeyService,
                configService);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            captured.add(new CapturedRequest(exchange.getRequestHeaders(), body));
            byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(respondCode, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        server.start();
        port = server.getAddress().getPort();
        when(apiKeyService.findSigningSecret(1L)).thenReturn(Optional.of(SECRET));
        stubMerchantWebhookUrl("http://localhost:" + port + "/callback");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void postsSignedPayloadAndMarksSuccess() {
        WebhookEvent event = pendingEvent(0);
        when(eventMapper.selectById(100L)).thenReturn(event);

        boolean delivered = service.deliver(100L);

        assertTrue(delivered);
        CapturedRequest request = captured.get(0);
        String timestamp = request.headers.getFirst(WebhookDeliveryService.HEADER_TIMESTAMP);
        // 签名可被商户用同一 Secret 复算验证
        assertEquals(HmacSigner.sign(timestamp + "." + request.body, SECRET),
                request.headers.getFirst(WebhookDeliveryService.HEADER_SIGNATURE),
                () -> "ts=" + timestamp + ", body=" + request.body);
        assertEquals("evt-1", request.headers.getFirst(WebhookDeliveryService.HEADER_EVENT_ID));
        assertEquals("PAYMENT_SUCCEEDED", request.headers.getFirst(WebhookDeliveryService.HEADER_EVENT_TYPE));
        // 投出的报文与库中 payload 完全一致
        assertEquals(event.getPayload(), request.body);

        // 首投成功：日志 attempt=1，状态推进到 SUCCESS
        ArgumentCaptor<WebhookDelivery> deliveryCaptor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryMapper).insert(deliveryCaptor.capture());
        assertEquals(1, deliveryCaptor.getValue().getAttemptNo());
        assertTrue(deliveryCaptor.getValue().getSuccess());
        assertEquals(200, deliveryCaptor.getValue().getResponseCode());
        UpdateWrapper<WebhookEvent> update = capturedUpdate();
        assertTrue(update.getSqlSet().contains("status"));
        assertTrue(update.getParamNameValuePairs().containsValue(WebhookEvent.STATUS_SUCCESS));
    }

    @Test
    void failureSchedulesRetryWithBackoff() {
        respondCode = 500;
        WebhookEvent event = pendingEvent(0);
        when(eventMapper.selectById(100L)).thenReturn(event);

        boolean delivered = service.deliver(100L);

        // 失败也记日志（响应码 500），首次失败后按 1 分钟退避
        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getAttemptNo());
        assertEquals(500, captor.getValue().getResponseCode());
        UpdateWrapper<WebhookEvent> update = capturedUpdate();
        assertTrue(update.getSqlSet().contains("retry_count"));
        assertTrue(update.getParamNameValuePairs().containsValue(1));
        LocalDateTime nextRetryAt = (LocalDateTime) update.getParamNameValuePairs().values().stream()
                .filter(LocalDateTime.class::isInstance).findFirst().orElseThrow();
        assertTrue(nextRetryAt.isAfter(LocalDateTime.now().plusSeconds(30)));
    }

    @Test
    void exhaustedAfterMaxRetries() {
        respondCode = 500;
        // 已重试 4 次，本次为第 5 次重试（第 6 次投递），失败即耗尽
        WebhookEvent event = pendingEvent(4);
        when(eventMapper.selectById(100L)).thenReturn(event);

        service.deliver(100L);

        UpdateWrapper<WebhookEvent> update = capturedUpdate();
        assertTrue(update.getSqlSet().contains("status"));
        assertTrue(update.getParamNameValuePairs().containsValue(WebhookEvent.STATUS_EXHAUSTED));
        verify(deliveryMapper).insert(any(WebhookDelivery.class));
    }

    @Test
    void missingSubscriptionMarksExhaustedWithoutPosting() {
        when(eventMapper.selectById(100L)).thenReturn(pendingEvent(0));
        stubMerchantWebhookUrl(null);

        service.deliver(100L);

        assertTrue(captured.isEmpty());
        UpdateWrapper<WebhookEvent> update = capturedUpdate();
        assertTrue(update.getParamNameValuePairs().containsValue(WebhookEvent.STATUS_EXHAUSTED));
        verify(deliveryMapper, never()).insert(any(WebhookDelivery.class));
    }

    @Test
    void nonPendingEventSkipped() {
        WebhookEvent event = pendingEvent(0);
        event.setStatus(WebhookEvent.STATUS_SUCCESS);
        when(eventMapper.selectById(100L)).thenReturn(event);

        boolean delivered = service.deliver(100L);

        assertTrue(!delivered);
        assertTrue(captured.isEmpty());
    }

    private WebhookEvent pendingEvent(int retryCount) {
        WebhookEvent event = new WebhookEvent();
        event.setId(100L);
        event.setMerchantId(1L);
        event.setEventId("evt-1");
        event.setEventType("PAYMENT_SUCCEEDED");
        event.setBizOrderNo("PO20261018001");
        event.setPayload("{\"eventId\":\"evt-1\",\"eventType\":\"PAYMENT_SUCCEEDED\",\"data\":{}}");
        event.setStatus(WebhookEvent.STATUS_PENDING);
        event.setRetryCount(retryCount);
        return event;
    }

    private void stubMerchantWebhookUrl(String url) {
        Merchant merchant = new Merchant();
        merchant.setId(1L);
        merchant.setWebhookUrl(url);
        when(merchantService.getDetail(1L))
                .thenReturn(new MerchantDetailResponse(MerchantResponse.from(merchant), List.of()));
    }

    private UpdateWrapper<WebhookEvent> capturedUpdate() {
        ArgumentCaptor<UpdateWrapper<WebhookEvent>> captor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(eventMapper).update(isNull(), captor.capture());
        return captor.getValue();
    }

    private record CapturedRequest(com.sun.net.httpserver.Headers headers, String body) {
    }
}
