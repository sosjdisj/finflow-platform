package org.example.finflowbackend.webhook.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.common.util.HmacSigner;
import org.example.finflowbackend.merchant.service.ApiKeyService;
import org.example.finflowbackend.merchant.service.MerchantService;
import org.example.finflowbackend.system.config.ConfigKeys;
import org.example.finflowbackend.system.service.ConfigService;
import org.example.finflowbackend.webhook.entity.WebhookDelivery;
import org.example.finflowbackend.webhook.entity.WebhookEvent;
import org.example.finflowbackend.webhook.mapper.WebhookDeliveryMapper;
import org.example.finflowbackend.webhook.mapper.WebhookEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Webhook 投递核心（S32）：首次投递与重试共用本入口。
 * 流程：读 PENDING 事件 → 实时取商户回调地址与签名 Secret（模块间走 Service，铁律 4）
 * → HMAC-SHA256 签名（复用 S09 HmacSigner，签名串 = timestamp + "." + body，防重放）
 * → POST → 写投递日志（一次尝试一行，只增不改）→ 按 WebhookRetryPolicy 推进事件状态。
 * 不在事务内做外部 HTTP 调用；事件状态更新带 status='PENDING' 条件，
 * 与重试任务并发时同一事件最多被投两次（商户按 eventId 幂等），不会丢日志。
 */
@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    /** 请求头：事件 ID / 类型 / 签名时间戳（秒）/ 签名。 */
    public static final String HEADER_EVENT_ID = "X-FinFlow-Event-Id";
    public static final String HEADER_EVENT_TYPE = "X-FinFlow-Event-Type";
    public static final String HEADER_TIMESTAMP = "X-FinFlow-Timestamp";
    public static final String HEADER_SIGNATURE = "X-FinFlow-Signature";

    private static final int MAX_RESPONSE_BODY = 512;

    private final WebhookEventMapper eventMapper;
    private final WebhookDeliveryMapper deliveryMapper;
    private final MerchantService merchantService;
    private final ApiKeyService apiKeyService;
    private final ConfigService configService;
    private final RestClient restClient;

    public WebhookDeliveryService(WebhookEventMapper eventMapper,
                                  WebhookDeliveryMapper deliveryMapper,
                                  MerchantService merchantService,
                                  ApiKeyService apiKeyService,
                                  ConfigService configService) {
        this.eventMapper = eventMapper;
        this.deliveryMapper = deliveryMapper;
        this.merchantService = merchantService;
        this.apiKeyService = apiKeyService;
        this.configService = configService;
        // 连接超时 5s / 读超时 10s：回调地址是外部系统，不能拖死扫描任务
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * 投递一个事件（仅处理 PENDING 状态；SUCCESS/EXHAUSTED 直接返回）。
     *
     * @return 本次投递是否成功
     */
    public boolean deliver(Long eventRowId) {
        WebhookEvent event = eventMapper.selectById(eventRowId);
        if (event == null || !WebhookEvent.STATUS_PENDING.equals(event.getStatus())) {
            return false;
        }
        String webhookUrl = webhookUrlOf(event.getMerchantId());
        Optional<String> secret = apiKeyService.findSigningSecret(event.getMerchantId());
        if (webhookUrl == null || secret.isEmpty()) {
            log.warn("[Webhook投递] 商户缺少回调地址或可签名凭证，置为耗尽待手动重试: eventId={}, merchantId={}",
                    event.getEventId(), event.getMerchantId());
            markExhausted(event);
            return false;
        }

        long start = System.currentTimeMillis();
        Integer responseCode = null;
        String responseBody = null;
        boolean success = false;
        try {
            var result = post(webhookUrl, event, secret.get());
            responseCode = result.code();
            responseBody = result.body();
            success = responseCode >= 200 && responseCode < 300;
        } catch (RestClientException e) {
            // 连接失败/超时：记失败日志后按重试策略推进
            log.info("[Webhook投递] 请求异常: eventId={}, error={}", event.getEventId(), e.getMessage());
        }
        int durationMs = (int) (System.currentTimeMillis() - start);

        recordDelivery(event, success, responseCode, responseBody, durationMs);
        advance(event, success);
        return success;
    }

    /** 商户回调地址（实时读，商户改地址后下次重试即用新地址）。 */
    private String webhookUrlOf(Long merchantId) {
        return Optional.ofNullable(merchantService.getDetail(merchantId).merchant())
                .map(merchant -> merchant.webhookUrl())
                .orElse(null);
    }

    private PostResult post(String url, WebhookEvent event, String secret) {
        String body = event.getPayload();
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = HmacSigner.sign(timestamp + "." + body, secret);
        return restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_EVENT_ID, event.getEventId())
                .header(HEADER_EVENT_TYPE, event.getEventType())
                .header(HEADER_TIMESTAMP, timestamp)
                .header(HEADER_SIGNATURE, signature)
                .body(body)
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    byte[] raw = response.getBody().readAllBytes();
                    String text = new String(raw, StandardCharsets.UTF_8);
                    if (text.length() > MAX_RESPONSE_BODY) {
                        text = text.substring(0, MAX_RESPONSE_BODY);
                    }
                    return new PostResult(status.value(), text);
                });
    }

    private void recordDelivery(WebhookEvent event, boolean success, Integer responseCode,
                                String responseBody, int durationMs) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setEventRowId(event.getId());
        // attempt = 首投(1) + 已重试次数 + 1
        delivery.setAttemptNo(event.getRetryCount() + 1);
        delivery.setSuccess(success);
        delivery.setResponseCode(responseCode);
        delivery.setResponseBody(responseBody);
        delivery.setDurationMs(durationMs);
        deliveryMapper.insert(delivery);
    }

    /** 按投递结果推进事件状态：成功 → SUCCESS；失败 → 加一次重试或耗尽（规则集中在 RetryPolicy）。 */
    private void advance(WebhookEvent event, boolean success) {
        if (success) {
            eventMapper.update(null, Wrappers.<WebhookEvent>update()
                    .eq("id", event.getId()).eq("status", WebhookEvent.STATUS_PENDING)
                    .set("status", WebhookEvent.STATUS_SUCCESS));
            return;
        }
        // 最大重试次数实时读配置中心（S37），调参后无需重启
        int newRetryCount = event.getRetryCount() + 1;
        if (WebhookRetryPolicy.exhausted(newRetryCount,
                (int) configService.getLong(ConfigKeys.WEBHOOK_MAX_RETRIES))) {
            markExhausted(event);
            return;
        }
        LocalDateTime nextRetryAt = LocalDateTime.now().plus(WebhookRetryPolicy.backoff(newRetryCount));
        eventMapper.update(null, Wrappers.<WebhookEvent>update()
                .eq("id", event.getId()).eq("status", WebhookEvent.STATUS_PENDING)
                .set("retry_count", newRetryCount)
                .set("next_retry_at", nextRetryAt));
    }

    /** 重置为 PENDING 并立即投递一次（手动重试入口）。 */
    public boolean deliverAfterReset(WebhookEvent event) {
        eventMapper.update(null, Wrappers.<WebhookEvent>update()
                .eq("id", event.getId())
                .set("status", WebhookEvent.STATUS_PENDING)
                .set("retry_count", 0)
                .set("next_retry_at", LocalDateTime.now()));
        return deliver(event.getId());
    }

    private void markExhausted(WebhookEvent event) {
        eventMapper.update(null, Wrappers.<WebhookEvent>update()
                .eq("id", event.getId()).eq("status", WebhookEvent.STATUS_PENDING)
                .set("status", WebhookEvent.STATUS_EXHAUSTED));
    }

    /** 商户响应（状态码 + 截断后的响应体）。 */
    record PostResult(int code, String body) {
    }
}
