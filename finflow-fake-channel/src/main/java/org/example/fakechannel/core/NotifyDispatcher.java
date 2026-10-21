package org.example.fakechannel.core;

import jakarta.annotation.PreDestroy;
import org.example.fakechannel.config.FakeChannelProperties;
import org.example.fakechannel.sign.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 异步通知派发器：受理后延迟发送，并可选重复推送（模拟渠道重复通知）。
 *
 * <p>用自建的单线程 daemon 调度器，避免占用容器线程；HTTP 用 JDK 内置 HttpClient。
 * 通知发送失败/超时只记日志，绝不影响受理主链路。</p>
 */
@Component
public class NotifyDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotifyDispatcher.class);

    private final FakeChannelProperties properties;
    private final ChannelLedger ledger;
    private final SignatureService signatureService;
    private final HttpClient httpClient;
    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "fake-channel-notify");
        t.setDaemon(true);
        return t;
    });

    public NotifyDispatcher(FakeChannelProperties properties, ChannelLedger ledger,
                            SignatureService signatureService) {
        this.properties = properties;
        this.ledger = ledger;
        this.signatureService = signatureService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /** 延迟 payDelaySeconds 秒后决定支付结果并推送通知。 */
    public void schedulePaymentResult(String channelCode, String channelOrderNo, String platformOrderNo) {
        long delay = properties.payDelaySeconds();
        scheduler.schedule(() -> dispatchPayment(channelCode, channelOrderNo, platformOrderNo),
                delay, TimeUnit.SECONDS);
        log.info("已排定支付结果通知 channelCode={} channelOrderNo={} delaySeconds={}",
                channelCode, channelOrderNo, delay);
    }

    /** 延迟 payDelaySeconds 秒后决定退款结果并推送通知。 */
    public void scheduleRefundResult(String channelCode, String channelOrderNo,
                                     String platformOrderNo, String platformRefundNo, String channelRefundNo) {
        long delay = properties.payDelaySeconds();
        scheduler.schedule(() -> dispatchRefund(channelCode, channelOrderNo, platformOrderNo,
                        platformRefundNo, channelRefundNo),
                delay, TimeUnit.SECONDS);
        log.info("已排定退款结果通知 channelCode={} channelRefundNo={} delaySeconds={}",
                channelCode, channelRefundNo, delay);
    }

    private void dispatchPayment(String channelCode, String channelOrderNo, String platformOrderNo) {
        boolean failed = random.nextDouble() < properties.failRate();
        String result = failed ? "FAILED" : "SUCCESS";
        String message = failed ? "渠道模拟支付失败" : "渠道支付成功";
        ledger.updatePayStatus(channelOrderNo, result);
        log.info("渠道支付结果生成 channelCode={} channelOrderNo={} result={}", channelCode, channelOrderNo, result);

        for (int i = 1; i <= properties.notifyTimes(); i++) {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String body = paymentNotifyBody(channelCode, channelOrderNo, platformOrderNo, result, message, timestamp);
            boolean ok = pushNotification(channelCode, channelOrderNo, i, timestamp, body);
            log.info("支付通知推送 channelCode={} channelOrderNo={} 第{}/{}次 结果={}",
                    channelCode, channelOrderNo, i, properties.notifyTimes(), ok ? "已送达" : "失败");
            if (!sleepGap()) {
                return;
            }
        }
    }

    private void dispatchRefund(String channelCode, String channelOrderNo, String platformOrderNo,
                                String platformRefundNo, String channelRefundNo) {
        boolean failed = random.nextDouble() < properties.failRate();
        String result = failed ? "FAILED" : "SUCCESS";
        String message = failed ? "渠道模拟退款失败" : "渠道退款成功";
        ledger.updateRefundStatus(channelRefundNo, result);
        if (!failed) {
            ledger.markRefunded(channelOrderNo);
        }
        log.info("渠道退款结果生成 channelCode={} channelRefundNo={} result={}",
                channelCode, channelRefundNo, result);

        for (int i = 1; i <= properties.notifyTimes(); i++) {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String body = refundNotifyBody(channelCode, channelOrderNo, platformOrderNo,
                    platformRefundNo, channelRefundNo, result, message, timestamp);
            boolean ok = pushNotification(channelCode, channelOrderNo, i, timestamp, body);
            log.info("退款通知推送 channelCode={} channelRefundNo={} 第{}/{}次 结果={}",
                    channelCode, channelRefundNo, i, properties.notifyTimes(), ok ? "已送达" : "失败");
            if (!sleepGap()) {
                return;
            }
        }
    }

    /** 发送单次通知，签名头与 body 用同一时间戳。失败只记日志。 */
    private boolean pushNotification(String channelCode, String orderNo, int attempt,
                                     String timestamp, String body) {
        String signature = signatureService.sign(timestamp, channelCode, body);
        String url = notifyUrl(channelCode);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("X-Channel-Code", channelCode)
                    .header("X-Timestamp", timestamp)
                    .header("X-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("通知返回非 2xx channelCode={} orderNo={} 第{}次 status={}",
                        channelCode, orderNo, attempt, response.statusCode());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("通知发送失败 channelCode={} orderNo={} 第{}次 url={} err={}",
                    channelCode, orderNo, attempt, url, e.toString());
            return false;
        }
    }

    /** 相邻通知间隔；被打断返回 false 终止本轮推送。 */
    private boolean sleepGap() {
        int gap = properties.notifyGapSeconds();
        if (gap <= 0) {
            return true;
        }
        try {
            Thread.sleep(gap * 1000L);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String notifyUrl(String channelCode) {
        String base = properties.notifyUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + channelCode + "/notify";
    }

    private static String paymentNotifyBody(String channelCode, String channelOrderNo, String platformOrderNo,
                                            String result, String message, String timestamp) {
        return "{"
                + "\"channelCode\":\"" + escape(channelCode) + "\","
                + "\"channelOrderNo\":\"" + escape(channelOrderNo) + "\","
                + "\"platformOrderNo\":\"" + escape(platformOrderNo) + "\","
                + "\"result\":\"" + result + "\","
                + "\"message\":\"" + escape(message) + "\","
                + "\"timestamp\":" + timestamp
                + "}";
    }

    private static String refundNotifyBody(String channelCode, String channelOrderNo, String platformOrderNo,
                                           String platformRefundNo, String channelRefundNo,
                                           String result, String message, String timestamp) {
        return "{"
                + "\"channelCode\":\"" + escape(channelCode) + "\","
                + "\"channelOrderNo\":\"" + escape(channelOrderNo) + "\","
                + "\"platformOrderNo\":\"" + escape(platformOrderNo) + "\","
                + "\"result\":\"" + result + "\","
                + "\"message\":\"" + escape(message) + "\","
                + "\"timestamp\":" + timestamp + ","
                + "\"platformRefundNo\":\"" + escape(platformRefundNo) + "\","
                + "\"channelRefundNo\":\"" + escape(channelRefundNo) + "\","
                + "\"type\":\"REFUND\""
                + "}";
    }

    /** 极简 JSON 字符串转义（值来自控制器/随机串，均为可控的 ASCII/中文）。 */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        log.info("假渠道通知调度器已关闭");
    }
}
