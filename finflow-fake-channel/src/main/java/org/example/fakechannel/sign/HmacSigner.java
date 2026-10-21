package org.example.fakechannel.sign;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * HMAC-SHA256 签名工具：输出小写十六进制。
 *
 * <p>注意：新模块（假渠道模拟器）是独立部署的进程，不与 finflow-backend 共享代码，
 * 因此这里刻意与后端 {@code org.example.finflowbackend.common.util.HmacSigner} 逻辑重复，
 * 以保证渠道侧协议实现自洽、可单独发布。两者算法与输出格式必须保持一致（小写 hex）。</p>
 */
public final class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacSigner() {
    }

    /** 用 secret 对 data 做 HMAC-SHA256，返回小写十六进制字符串。 */
    public static String sign(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return hex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 签名失败", e);
        }
    }

    /** 字节转小写十六进制。 */
    public static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
