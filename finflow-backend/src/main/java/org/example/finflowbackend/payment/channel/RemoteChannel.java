package org.example.finflowbackend.payment.channel;

import org.example.finflowbackend.payment.service.PaymentTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 真实渠道骨架：以 HTTP 调用独立进程的渠道服务（本地为假渠道模拟器），出站请求带 HMAC 签名头。
 * 受理类交互只返回「受理中」，渠道异步结果不经此路径，而由渠道回调通知端点驱动订单状态。
 * 渠道应答沿用渠道信封：成功 code=10000 取 data；业务失败 code=40004 返回未受理。
 * 子类只声明渠道编码。
 */
public abstract class RemoteChannel implements PaymentChannel {

    private static final Logger log = LoggerFactory.getLogger(RemoteChannel.class);

    /** 渠道信封成功码。 */
    private static final String CODE_SUCCESS = "10000";

    private final String code;
    private final RemoteChannelProperties properties;
    private final ChannelSignatureService signatureService;
    private final PaymentTransactionService transactionService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    protected RemoteChannel(String code, RemoteChannelProperties properties,
                            ChannelSignatureService signatureService,
                            PaymentTransactionService transactionService, ObjectMapper objectMapper) {
        this.code = code;
        this.properties = properties;
        this.signatureService = signatureService;
        this.transactionService = transactionService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build();
    }

    @Override
    public final String channelCode() {
        return code;
    }

    @Override
    public final ChannelResult createPayment(ChannelRequest request) {
        Envelope env = call(payPath(), payBody(request));
        if (!env.ok()) {
            return new ChannelResult(false, null, null, env.message());
        }
        String channelOrderNo = str(env.data(), "channelOrderNo");
        ChannelResult result = new ChannelResult(true, channelOrderNo, str(env.data(), "status"), "受理中");
        transactionService.record(request.orderId(), code, "PAYMENT", channelOrderNo, request, result);
        return result;
    }

    @Override
    public final ChannelResult queryPayment(ChannelRequest request) {
        Envelope env = call(payPath() + "/query", queryBody(request.channelOrderNo()));
        if (!env.ok()) {
            return new ChannelResult(false, request.channelOrderNo(), null, env.message());
        }
        return new ChannelResult(true, str(env.data(), "channelOrderNo"), str(env.data(), "status"), "查询成功");
    }

    @Override
    public final ChannelResult refund(ChannelRequest request) {
        Envelope env = call(refundPath(), refundBody(request));
        if (!env.ok()) {
            return new ChannelResult(false, null, null, env.message());
        }
        String channelRefundNo = str(env.data(), "channelRefundNo");
        ChannelResult result = new ChannelResult(true, channelRefundNo, str(env.data(), "status"), "退款受理中");
        transactionService.record(request.orderId(), code, "REFUND", channelRefundNo, request, result);
        return result;
    }

    @Override
    public final ChannelResult queryRefund(ChannelRequest request) {
        Envelope env = call(refundPath() + "/query", queryBody(request.channelOrderNo()));
        if (!env.ok()) {
            return new ChannelResult(false, request.channelOrderNo(), null, env.message());
        }
        return new ChannelResult(true, str(env.data(), "channelRefundNo"), str(env.data(), "status"), "查询成功");
    }

    /** 单次渠道调用：拼签名头 → POST → 解析信封。任何异常都转为未受理，不影响主链路事务。 */
    private Envelope call(String path, String body) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = signatureService.sign(timestamp, code, body);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                    .timeout(Duration.ofMillis(properties.readTimeoutMs()))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("X-Channel-Code", code)
                    .header("X-Timestamp", timestamp)
                    .header("X-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return new Envelope(false, "渠道 HTTP 状态 " + response.statusCode(), null);
            }
            Map<?, ?> root = objectMapper.readValue(response.body(), Map.class);
            if (!CODE_SUCCESS.equals(root.get("code"))) {
                return new Envelope(false, asString(root.get("message")), null);
            }
            return new Envelope(true, asString(root.get("message")), asMap(root.get("data")));
        } catch (Exception e) {
            log.warn("渠道调用失败 channelCode={} path={} err={}", code, path, e.toString());
            return new Envelope(false, "渠道调用异常: " + e.getMessage(), null);
        }
    }

    private String baseUrl() {
        String base = properties.baseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private String payPath() {
        return "/channel/" + code + "/pay";
    }

    private String refundPath() {
        return "/channel/" + code + "/refund";
    }

    private String payBody(ChannelRequest request) {
        return "{"
                + "\"platformOrderNo\":\"" + escape(request.platformOrderNo()) + "\","
                + "\"merchantOrderNo\":\"" + escape(request.merchantOrderNo()) + "\","
                + "\"amount\":" + request.amount() + ","
                + "\"subject\":\"" + escape(request.subject()) + "\","
                + "\"paymentMethod\":\"" + escape(request.paymentMethod()) + "\""
                + "}";
    }

    private String refundBody(ChannelRequest request) {
        return "{"
                + "\"channelOrderNo\":\"" + escape(request.channelOrderNo()) + "\","
                + "\"platformRefundNo\":\"" + escape(request.refundNo()) + "\","
                + "\"amount\":" + request.amount()
                + "}";
    }

    private String queryBody(String channelOrderNo) {
        return "{\"channelOrderNo\":\"" + escape(channelOrderNo) + "\"}";
    }

    private static String str(Map<String, Object> data, String key) {
        return data == null ? null : asString(data.get(key));
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    /** 极简 JSON 字符串转义（值均为可控 ASCII/中文）。 */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 渠道应答解析结果：ok=false 时 message 为失败原因。 */
    private record Envelope(boolean ok, String message, Map<String, Object> data) {
    }
}
