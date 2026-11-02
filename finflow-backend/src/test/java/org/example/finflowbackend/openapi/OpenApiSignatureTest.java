package org.example.finflowbackend.openapi;

import org.example.finflowbackend.common.util.HmacSigner;
import org.example.finflowbackend.openapi.security.OpenApiSignature;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M12 签名规范单测：规范串必须是商户端可复现的单一事实源，
 * query 排序、空 body、时间戳窗口、常量时间比较各有一条断言。
 */
class OpenApiSignatureTest {

    private static final String SECRET = "fs_test_secret";

    @Test
    void canonicalSortsQueryAndHashesBody() {
        String canonical = OpenApiSignature.canonical("post", "/open/v1/payments", "b=2&a=1",
                "{\"amount\":100}".getBytes(StandardCharsets.UTF_8), "1700000000000", "n1");

        String expectedBodyHash = HmacSigner.sha256Hex("{\"amount\":100}");
        assertEquals("POST\n/open/v1/payments\na=1&b=2\n" + expectedBodyHash + "\n1700000000000\nn1", canonical);
    }

    @Test
    void canonicalUsesEmptyBodyHashAndEmptyQuery() {
        String canonical = OpenApiSignature.canonical("GET", "/open/v1/payments/P1", null, null, "1700000000000", "n2");

        assertEquals("GET\n/open/v1/payments/P1\n\n" + HmacSigner.sha256Hex("") + "\n1700000000000\nn2", canonical);
    }

    @Test
    void matchesAcceptsCaseInsensitiveSignature() {
        String canonical = OpenApiSignature.canonical("GET", "/open/v1/payments/P1", null, null,
                "1700000000000", "n3");
        String signature = HmacSigner.sign(canonical, SECRET);

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
}
