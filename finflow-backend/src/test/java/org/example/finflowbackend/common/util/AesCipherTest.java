package org.example.finflowbackend.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AES-256-GCM 工具单测：加解密往返、密文随机性、篡改检测、密钥隔离、格式校验。
 */
class AesCipherTest {

    private static final String KEY = "finflow-test-key";

    @Test
    void roundTrip() {
        String secret = "fs_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        String ciphertext = AesCipher.encrypt(secret, KEY);
        String decrypted = AesCipher.decrypt(ciphertext, KEY);

        assertEquals(secret, decrypted);
        assertTrue(ciphertext.startsWith("v1:"));
        assertNotEquals(secret, ciphertext);
    }

    @Test
    void samePlaintextYieldsDifferentCiphertext() {
        // 随机 IV：同一明文两次加密密文不同（不可重放分析）
        assertNotEquals(AesCipher.encrypt("fs_same", KEY), AesCipher.encrypt("fs_same", KEY));
    }

    @Test
    void tamperedCiphertextFails() {
        String ciphertext = AesCipher.encrypt("fs_secret", KEY);
        String tampered = ciphertext.substring(0, ciphertext.length() - 2) + "xx";

        assertThrows(IllegalArgumentException.class, () -> AesCipher.decrypt(tampered, KEY));
    }

    @Test
    void wrongKeyFails() {
        String ciphertext = AesCipher.encrypt("fs_secret", KEY);

        assertThrows(IllegalArgumentException.class, () -> AesCipher.decrypt(ciphertext, "another-key"));
    }

    @Test
    void malformedCiphertextFails() {
        assertThrows(IllegalArgumentException.class, () -> AesCipher.decrypt("not-cipher", KEY));
        assertThrows(IllegalArgumentException.class, () -> AesCipher.decrypt(null, KEY));
    }
}
