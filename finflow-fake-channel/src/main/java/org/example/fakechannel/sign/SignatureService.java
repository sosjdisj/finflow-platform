package org.example.fakechannel.sign;

import org.example.fakechannel.config.FakeChannelProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 渠道侧签名服务：生成签名 + 校验入站签名（含时间戳防重放窗口）。
 *
 * <p>待签串（canonical string）：
 * <pre>
 *   signContent = timestamp + "\n" + channelCode + "\n" + body
 *   X-Signature  = hex( HMAC-SHA256(secret, signContent) )
 * </pre>
 * 其中 body 是原始请求体字符串（GET/无体时为空串 ""）。</p>
 */
@Component
public class SignatureService {

    /** 时间戳有效窗口：5 分钟，超出即视为重放/过期。 */
    private static final long WINDOW_MS = 5 * 60 * 1000L;

    private final String secret;

    public SignatureService(FakeChannelProperties properties) {
        this.secret = properties.secret();
    }

    /** 拼接待签串。 */
    public static String canonical(String timestamp, String channelCode, String body) {
        return timestamp + "\n" + channelCode + "\n" + (body == null ? "" : body);
    }

    /** 生成签名（小写 hex）。 */
    public String sign(String timestamp, String channelCode, String body) {
        return HmacSigner.sign(canonical(timestamp, channelCode, body), secret);
    }

    /** 校验签名 + 时间戳窗口，使用当前时间作为 now。 */
    public VerifyResult verify(String timestamp, String channelCode, String body, String signature) {
        return verify(timestamp, channelCode, body, signature, System.currentTimeMillis());
    }

    /** 校验签名 + 时间戳窗口（显式传入 now，便于测试）。 */
    public VerifyResult verify(String timestamp, String channelCode, String body, String signature, long now) {
        if (timestamp == null || channelCode == null || signature == null) {
            return VerifyResult.MALFORMED;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return VerifyResult.MALFORMED;
        }
        if (Math.abs(now - ts) > WINDOW_MS) {
            return VerifyResult.TIMESTAMP_EXPIRED;
        }
        String expected = sign(timestamp, channelCode, body);
        return constantTimeEquals(expected, signature) ? VerifyResult.OK : VerifyResult.SIGNATURE_MISMATCH;
    }

    /** 常量时间比较，避免签名逐字节比较带来的时序侧信道。 */
    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    /** 验签结果。 */
    public enum VerifyResult {
        /** 通过。 */
        OK,
        /** 缺字段或时间戳不可解析。 */
        MALFORMED,
        /** 时间戳超出 5 分钟窗口。 */
        TIMESTAMP_EXPIRED,
        /** 签名不匹配。 */
        SIGNATURE_MISMATCH
    }
}
