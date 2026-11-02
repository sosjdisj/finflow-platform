package org.example.finflowgateway.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M13 网关侧签名规范单测：向量与 backend 的 OpenApiSignatureTest 完全一致，
 * 两侧实现一漂移，这里或那边就会红。
 */
class OpenApiSignatureTest {

    private static final String SECRET = "fs_test_secret";

    @Test
    void canonicalSortsQueryAndHashesBody() {
        String canonical = OpenApiSignature.canonical("post", "/open/v1/payments", "b=2&a=1",
                "{\"amount\":100}".getBytes(StandardCharsets.UTF_8), "1700000000000", "n1");

        assertEquals("POST\n/open/v1/payments\na=1&b=2\n" + sha256Hex("{\"amount\":100}")
                + "\n1700000000000\nn1", canonical);
    }

    @Test
    void canonicalUsesEmptyBodyHashAndEmptyQuery() {
        String canonical = OpenApiSignature.canonical("GET", "/open/v1/payments/P1", null, null,
                "1700000000000", "n2");

        assertEquals("GET\n/open/v1/payments/P1\n\n" + sha256Hex("") + "\n1700000000000\nn2", canonical);
    }

    @Test
    void matchesAcceptsCaseInsensitiveSignature() {
        String canonical = OpenApiSignature.canonical("GET", "/open/v1/payments/P1", null, null,
                "1700000000000", "n3");
        String signature = sign(canonical, SECRET);

        assertTrue(OpenApiSignature.matches(SECRET, canonical, signature.toUpperCase()));
        assertFalse(OpenApiSignature.matches(SECRET, canonical, signature.substring(1)));
        assertFalse(OpenApiSignature.matches(SECRET, canonical, null));
    }

    @Test
    void timestampWindowIsSymmetric() {
        long now = 1_700_000_000_000L;

        assertTrue(OpenApiSignature.timestampInWindow(now - 300_000L, now, 300));
        assertTrue(OpenApiSignature.timestampInWindow(now + 300_000L, now, 300));
        assertFalse(OpenApiSignature.timestampInWindow(now - 300_001L, now, 300));
        assertFalse(OpenApiSignature.timestampInWindow(now + 300_001L, now, 300));
    }

    private String sign(String canonical, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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

    private String sha256Hex(String text) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
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
