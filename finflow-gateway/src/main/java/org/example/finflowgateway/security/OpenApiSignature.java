package org.example.finflowgateway.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OpenAPI AK/SK 签名规范（M13，网关侧）：与 backend 的 OpenApiSignature 逐字节一致——
 *
 * <pre>
 * canonical = METHOD \n PATH \n 排序后的 query \n sha256(body) \n 时间戳 \n nonce
 * signature = hex(HMAC-SHA256(secret, canonical))
 * </pre>
 *
 * <p>这是商户端可复现的协议，不是业务逻辑：两侧实现由同一组固定向量测试锁定，
 * 任何一侧改动都必须先改协议和双方向量，不允许只改一边。</p>
 */
public final class OpenApiSignature {

    public static final String HEADER_API_KEY = "X-Api-Key";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_NONCE = "X-Nonce";
    public static final String HEADER_SIGNATURE = "X-Signature";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private OpenApiSignature() {
    }

    /** 构造规范串；body 允许 null（按空 body 处理）。 */
    public static String canonical(String method, String path, String rawQuery, byte[] body,
                                   String timestamp, String nonce) {
        String bodyText = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        return method.toUpperCase(Locale.ROOT) + "\n"
                + path + "\n"
                + sortedQuery(rawQuery) + "\n"
                + sha256Hex(bodyText) + "\n"
                + timestamp + "\n"
                + nonce;
    }

    /** 验签：常量时间比较，避免按字节提前失败暴露签名前缀。 */
    public static boolean matches(String secret, String canonical, String signature) {
        String expected = hmacSha256Hex(canonical, secret);
        String actual = signature == null ? "" : signature.toLowerCase(Locale.ROOT);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    /** 时间戳是否落在 [now - window, now + window]（毫秒）。 */
    public static boolean timestampInWindow(long timestampMillis, long nowMillis, int windowSeconds) {
        return Math.abs(nowMillis - timestampMillis) <= windowSeconds * 1000L;
    }

    private static String sortedQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        List<String> pairs = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            if (!pair.isBlank()) {
                pairs.add(pair);
            }
        }
        pairs.sort(String::compareTo);
        return String.join("&", pairs);
    }

    private static String sha256Hex(String data) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 摘要失败", e);
        }
    }

    private static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return hex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 签名失败", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
