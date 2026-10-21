package org.example.finflowbackend.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.finflowbackend.common.util.HmacSigner;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 集成/单测用渠道 HTTP 桩：同 JVM 的 JDK HttpServer，复刻假渠道服务的协议行为——
 * 受理支付/退款（幂等）+ 延迟异步通知（带 HMAC 签名回推后端 /api/v1/channels/{code}/notify）。
 * 行为旋钮：{@link #setFailRate(double)} 控制通知结果成功/失败，{@link #setDelayMs(long)} 控制受理到通知的延迟。
 * 进程级单例：{@link #get()}；通知目标地址由 {@link #setNotifyBaseUrl(String)} 在用例中注入。
 */
public final class FakeChannelStub {

    private static final String SECRET = "finflow-fake-channel-secret";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final FakeChannelStub INSTANCE = new FakeChannelStub();

    /** 渠道单号前缀 → 渠道码：MOCK_WALLET → W、MOCK_BANK → B、MOCK_CARD → C 之类，仅取首字母。 */
    private final Map<String, String> payIndexByPlatformOrderNo = new ConcurrentHashMap<>();
    private final Map<String, PayOrder> paysByChannelOrderNo = new ConcurrentHashMap<>();
    private final Map<String, String> refundIndexByPlatformRefundNo = new ConcurrentHashMap<>();
    private final Map<String, RefundOrder> refundsByChannelRefundNo = new ConcurrentHashMap<>();

    private final HttpServer server;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "fake-channel-stub-notify");
        t.setDaemon(true);
        return t;
    });
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private volatile double failRate = 0.0;
    private volatile long delayMs = 0L;
    private volatile String notifyBaseUrl;

    public static FakeChannelStub get() {
        return INSTANCE;
    }

    public FakeChannelStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("渠道桩启动失败", e);
        }
        ExecutorService pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "fake-channel-stub-http");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(pool);
        server.createContext("/", this::dispatch);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void setNotifyBaseUrl(String notifyBaseUrl) {
        this.notifyBaseUrl = notifyBaseUrl;
    }

    public void setFailRate(double failRate) {
        this.failRate = failRate;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    /** 用例每轮重置行为旋钮（失败率归零、零延迟）。 */
    public void reset() {
        this.failRate = 0.0;
        this.delayMs = 0L;
    }

    public void close() {
        server.stop(0);
        scheduler.shutdownNow();
    }

    // ---- HTTP 路由 ----

    private void dispatch(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String[] parts = path.split("/"); // ["", "channel", code, action, sub?]
            if (parts.length < 4 || !"channel".equals(parts[1])) {
                respond(exchange, 404, bizFail("未知路径"));
                return;
            }
            String code = parts[2];
            String action = parts[3];
            String sub = parts.length > 4 ? parts[4] : null;
            if (!verify(exchange, code, body)) {
                respond(exchange, 401, "{\"code\":\"40001\",\"message\":\"验签失败\",\"data\":null}");
                return;
            }
            if ("pay".equals(action) && sub == null) {
                handlePay(exchange, code, body);
            } else if ("pay".equals(action)) {
                handlePayQuery(exchange, body);
            } else if ("refund".equals(action) && sub == null) {
                handleRefund(exchange, code, body);
            } else if ("refund".equals(action)) {
                handleRefundQuery(exchange, body);
            } else {
                respond(exchange, 404, bizFail("未知接口"));
            }
        } catch (Exception e) {
            respond(exchange, 500, bizFail("桩内部异常: " + e.getMessage()));
        }
    }

    private void handlePay(HttpExchange exchange, String code, String body) throws IOException {
        Map<String, Object> req = MAPPER.readValue(body, Map.class);
        String platformOrderNo = str(req.get("platformOrderNo"));
        long amount = ((Number) req.get("amount")).longValue();
        String channelOrderNo;
        synchronized (payIndexByPlatformOrderNo) {
            String existing = payIndexByPlatformOrderNo.get(platformOrderNo);
            if (existing != null) {
                channelOrderNo = existing;
            } else {
                channelOrderNo = nextOrderNo(code, false);
                paysByChannelOrderNo.put(channelOrderNo,
                        new PayOrder(channelOrderNo, platformOrderNo, str(req.get("merchantOrderNo")), amount));
                payIndexByPlatformOrderNo.put(platformOrderNo, channelOrderNo);
                scheduleNotify(code, channelOrderNo, platformOrderNo, null, null);
            }
        }
        respond(exchange, 200, ok("{"
                + "\"channelOrderNo\":\"" + channelOrderNo + "\","
                + "\"status\":\"" + paysByChannelOrderNo.get(channelOrderNo).status() + "\"}"));
    }

    private void handlePayQuery(HttpExchange exchange, String body) throws IOException {
        Map<String, Object> req = MAPPER.readValue(body, Map.class);
        PayOrder order = paysByChannelOrderNo.get(str(req.get("channelOrderNo")));
        if (order == null) {
            respond(exchange, 200, bizFail("渠道单不存在"));
            return;
        }
        respond(exchange, 200, ok("{"
                + "\"channelOrderNo\":\"" + order.channelOrderNo + "\","
                + "\"status\":\"" + order.status() + "\"}"));
    }

    private void handleRefund(HttpExchange exchange, String code, String body) throws IOException {
        Map<String, Object> req = MAPPER.readValue(body, Map.class);
        String channelOrderNo = str(req.get("channelOrderNo"));
        String platformRefundNo = str(req.get("platformRefundNo"));
        if (channelOrderNo != null && paysByChannelOrderNo.get(channelOrderNo) == null) {
            respond(exchange, 200, bizFail("渠道单不存在"));
            return;
        }
        long amount = ((Number) req.get("amount")).longValue();
        String channelRefundNo;
        synchronized (refundIndexByPlatformRefundNo) {
            String existing = refundIndexByPlatformRefundNo.get(platformRefundNo);
            if (existing != null) {
                channelRefundNo = existing;
            } else {
                channelRefundNo = nextOrderNo(code, true);
                refundsByChannelRefundNo.put(channelRefundNo,
                        new RefundOrder(channelRefundNo, platformRefundNo, channelOrderNo, amount));
                refundIndexByPlatformRefundNo.put(platformRefundNo, channelRefundNo);
                PayOrder pay = paysByChannelOrderNo.get(channelOrderNo);
                scheduleNotify(code, channelOrderNo, pay == null ? null : pay.platformOrderNo,
                        platformRefundNo, channelRefundNo);
            }
        }
        respond(exchange, 200, ok("{"
                + "\"channelRefundNo\":\"" + channelRefundNo + "\","
                + "\"status\":\"" + refundsByChannelRefundNo.get(channelRefundNo).status() + "\"}"));
    }

    private void handleRefundQuery(HttpExchange exchange, String body) throws IOException {
        Map<String, Object> req = MAPPER.readValue(body, Map.class);
        RefundOrder refund = refundsByChannelRefundNo.get(str(req.get("channelOrderNo")));
        if (refund == null) {
            respond(exchange, 200, bizFail("渠道退款单不存在"));
            return;
        }
        respond(exchange, 200, ok("{"
                + "\"channelRefundNo\":\"" + refund.channelRefundNo + "\","
                + "\"status\":\"" + refund.status() + "\"}"));
    }

    // ---- 异步通知 ----

    private void scheduleNotify(String code, String channelOrderNo, String platformOrderNo,
                                String platformRefundNo, String channelRefundNo) {
        Runnable task = () -> sendNotify(code, channelOrderNo, platformOrderNo, platformRefundNo, channelRefundNo);
        long delay = delayMs;
        if (delay > 0) {
            scheduler.schedule(task, delay, TimeUnit.MILLISECONDS);
        } else {
            scheduler.execute(task);
        }
    }

    private void sendNotify(String code, String channelOrderNo, String platformOrderNo,
                            String platformRefundNo, String channelRefundNo) {
        boolean failed = ThreadLocalRandom.current().nextDouble() < failRate;
        String result = failed ? "FAILED" : "SUCCESS";
        String message = failed ? "渠道模拟失败" : "渠道成功";
        String target = notifyBaseUrl;
        if (target == null) {
            return; // 未注入通知地址（单测场景），跳过推送
        }
        String body;
        if (platformRefundNo != null) {
            RefundOrder refund = refundsByChannelRefundNo.get(channelRefundNo);
            if (refund != null) {
                refund.status = result;
            }
            body = "{"
                    + "\"type\":\"REFUND\","
                    + "\"channelCode\":\"" + code + "\","
                    + "\"channelOrderNo\":\"" + nvl(channelOrderNo) + "\","
                    + "\"channelRefundNo\":\"" + nvl(channelRefundNo) + "\","
                    + "\"platformOrderNo\":\"" + nvl(platformOrderNo) + "\","
                    + "\"platformRefundNo\":\"" + nvl(platformRefundNo) + "\","
                    + "\"result\":\"" + result + "\","
                    + "\"message\":\"" + message + "\","
                    + "\"timestamp\":" + System.currentTimeMillis()
                    + "}";
        } else {
            PayOrder pay = paysByChannelOrderNo.get(channelOrderNo);
            if (pay != null) {
                pay.status = result;
            }
            body = "{"
                    + "\"channelCode\":\"" + code + "\","
                    + "\"channelOrderNo\":\"" + nvl(channelOrderNo) + "\","
                    + "\"platformOrderNo\":\"" + nvl(platformOrderNo) + "\","
                    + "\"result\":\"" + result + "\","
                    + "\"message\":\"" + message + "\","
                    + "\"timestamp\":" + System.currentTimeMillis()
                    + "}";
        }
        postSigned(target + "/" + code + "/notify", code, body);
    }

    private void postSigned(String url, String code, String body) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = HmacSigner.sign(canonical(timestamp, code, body), SECRET);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("X-Channel-Code", code)
                    .header("X-Timestamp", timestamp)
                    .header("X-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            // 渠道桩通知失败只记日志，不影响受理主链路
            System.err.println("[FakeChannelStub] 通知发送失败 url=" + url + " err=" + e);
        }
    }

    // ---- 校验与工具 ----

    private boolean verify(HttpExchange exchange, String code, String body) {
        String timestamp = exchange.getRequestHeaders().getFirst("X-Timestamp");
        String signature = exchange.getRequestHeaders().getFirst("X-Signature");
        if (timestamp == null || signature == null) {
            return false;
        }
        return HmacSigner.sign(canonical(timestamp, code, body), SECRET).equals(signature);
    }

    private static String canonical(String timestamp, String code, String body) {
        return timestamp + "\n" + code + "\n" + (body == null ? "" : body);
    }

    private static String nextOrderNo(String code, boolean refund) {
        String prefix = "M" + code.charAt(code.indexOf('_') + 1);
        return prefix + (refund ? "R" : "") + System.nanoTime();
    }

    private static String ok(String data) {
        return "{\"code\":\"10000\",\"message\":\"成功\",\"data\":" + data + "}";
    }

    private static String bizFail(String message) {
        return "{\"code\":\"40004\",\"message\":\"" + nvl(message) + "\",\"data\":null}";
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** 桩内支付单。 */
    private static final class PayOrder {
        private final String channelOrderNo;
        private final String platformOrderNo;
        private final String merchantOrderNo;
        private final long amount;
        private volatile String status = "PENDING";

        PayOrder(String channelOrderNo, String platformOrderNo, String merchantOrderNo, long amount) {
            this.channelOrderNo = channelOrderNo;
            this.platformOrderNo = platformOrderNo;
            this.merchantOrderNo = merchantOrderNo;
            this.amount = amount;
        }

        String status() {
            return status;
        }
    }

    /** 桩内退款单。 */
    private static final class RefundOrder {
        private final String channelRefundNo;
        private final String platformRefundNo;
        private final String channelOrderNo;
        private final long amount;
        private volatile String status = "PROCESSING";

        RefundOrder(String channelRefundNo, String platformRefundNo, String channelOrderNo, long amount) {
            this.channelRefundNo = channelRefundNo;
            this.platformRefundNo = platformRefundNo;
            this.channelOrderNo = channelOrderNo;
            this.amount = amount;
        }

        String status() {
            return status;
        }
    }
}
