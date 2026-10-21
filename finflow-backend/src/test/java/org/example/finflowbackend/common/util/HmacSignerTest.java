package org.example.finflowbackend.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * HMAC-SHA256 签名单测：使用 RFC 4231 / 公开标准测试向量锚定正确性。
 */
class HmacSignerTest {

    @Test
    void matchesStandardVector() {
        // HMAC-SHA256("key", "The quick brown fox jumps over the lazy dog") 的公认值
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                HmacSigner.sign("The quick brown fox jumps over the lazy dog", "key"));
    }

    @Test
    void differentSecretYieldsDifferentSignature() {
        String signature = HmacSigner.sign("payload", "secret-a");
        assertNotEquals(signature, HmacSigner.sign("payload", "secret-b"));
    }

    @Test
    void differentDataYieldsDifferentSignature() {
        String signature = HmacSigner.sign("payload-1", "secret");
        assertNotEquals(signature, HmacSigner.sign("payload-2", "secret"));
    }

    @Test
    void sameInputIsDeterministic() {
        assertEquals(HmacSigner.sign("body", "secret"), HmacSigner.sign("body", "secret"));
    }

    @Test
    void sha256HexMatchesKnownVector() {
        // SHA-256("abc") 的公认值
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                HmacSigner.sha256Hex("abc"));
    }
}
