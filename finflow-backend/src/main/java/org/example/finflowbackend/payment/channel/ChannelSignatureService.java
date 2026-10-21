package org.example.finflowbackend.payment.channel;

import org.example.finflowbackend.common.util.HmacSigner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 渠道协议签名服务（后端侧）：出站请求生成签名头 + 入站回调通知校验签名（含时间戳防重放窗口）。
 * 与渠道服务共用同一套待签串与算法：
 * <pre>
 *   signContent = timestamp + "\n" + channelCode + "\n" + body
 *   X-Signature = 小写 hex( HMAC-SHA256(secret, signContent) )
 * </pre>
 * body 为原始请求体字符串（无体时为空串）。
 */
@Component
public class ChannelSignatureService {

    /** 时间戳有效窗口：5 分钟，超出即视为重放/过期。 */
    private static final long WINDOW_MS = 5 * 60 * 1000L;

    private final String secret;

    public ChannelSignatureService(RemoteChannelProperties properties) {
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

    /** 校验入站通知签名 + 时间戳窗口。 */
    public boolean verify(String timestamp, String channelCode, String body, String signature) {
        if (timestamp == null || channelCode == null || signature == null) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(System.currentTimeMillis() - ts) > WINDOW_MS) {
            return false;
        }
        // 常量时间比较，避免逐字节比较的时序侧信道
        return MessageDigest.isEqual(
                sign(timestamp, channelCode, body).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
