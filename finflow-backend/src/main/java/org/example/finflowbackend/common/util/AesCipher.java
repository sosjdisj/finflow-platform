package org.example.finflowbackend.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 可逆加密工具（S32）：Webhook HMAC 签名需要明文 Secret，
 * Secret 以密文形式落库，投递时解密。任意长度的配置密钥经 SHA-256 归一为 32 字节 AES key。
 * 密文格式：v1:{base64(iv)}:{base64(cipher)}，12 字节随机 IV + 128 位认证标签，
 * 篡改或换密钥解密均抛异常（GCM 认证失败）。
 */
public final class AesCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String PREFIX = "v1:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private AesCipher() {
    }

    public static String encrypt(String plaintext, String keyConfig) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, toKey(keyConfig), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    /** 解密失败（密文被篡改、密钥不符、格式非法）一律抛 IllegalArgumentException。 */
    public static String decrypt(String ciphertext, String keyConfig) {
        if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
            throw new IllegalArgumentException("密文格式非法");
        }
        try {
            String[] parts = ciphertext.substring(PREFIX.length()).split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("密文格式非法");
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, toKey(keyConfig), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("解密失败（密钥不符或密文被篡改）", e);
        }
    }

    /** 配置密钥（任意长度字符串）→ SHA-256 → 32 字节 AES-256 key。 */
    private static SecretKeySpec toKey(String keyConfig) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(keyConfig.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("密钥归一失败", e);
        }
    }
}
