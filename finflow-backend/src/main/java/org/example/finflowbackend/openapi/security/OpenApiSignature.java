package org.example.finflowbackend.openapi.security;

import org.example.finflowbackend.common.util.HmacSigner;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OpenAPI AK/SK 签名规范（M12，单一事实源）：
 *
 * <pre>
 * canonical = METHOD \n PATH \n 排序后的 query \n sha256(body) \n 时间戳 \n nonce
 * signature = hex(HMAC-SHA256(secret, canonical))
 * </pre>
 *
 * <p>query 按「编码后的 k=v 字符串」自然序排序后以 & 连接，空 query 为空串；
 * body 为空时取空串的 SHA-256。商户端与服务端必须用同一份规范串，任何差异都会导致验签失败。</p>
 */
public final class OpenApiSignature {

    public static final String HEADER_API_KEY = "X-Api-Key";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_NONCE = "X-Nonce";
    public static final String HEADER_SIGNATURE = "X-Signature";

    private OpenApiSignature() {
    }

    /** 构造规范串；body 允许 null（按空 body 处理）。 */
    public static String canonical(String method, String path, String rawQuery, byte[] body,
                                   String timestamp, String nonce) {
        String bodyText = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        return method.toUpperCase(Locale.ROOT) + "\n"
                + path + "\n"
                + sortedQuery(rawQuery) + "\n"
                + HmacSigner.sha256Hex(bodyText) + "\n"
                + timestamp + "\n"
                + nonce;
    }

    /** 验签：常量时间比较，避免按字节提前失败暴露签名前缀。 */
    public static boolean matches(String secret, String canonical, String signature) {
        String expected = HmacSigner.sign(canonical, secret);
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
}
