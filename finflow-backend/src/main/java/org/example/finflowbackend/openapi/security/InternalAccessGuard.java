package org.example.finflowbackend.openapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部接口令牌（M13）：只用于网关 ↔ backend 的内部调用，与商户 AK/SK 是两套体系。
 *
 * <p>网关在两个位置带它：取注入档案（/api/v1/internal/openapi/profiles）时做身份凭证；
 * 转发商户业务请求时作为「已验签」的可信标记。比较用常量时间，避免按字节提前失败。
 * 该令牌是部署级共享密钥，生产必须用环境变量覆盖，且 backend 只在内网可达。</p>
 */
@Component
public class InternalAccessGuard {

    public static final String HEADER_TOKEN = "X-Finflow-Internal-Token";

    /** 网关验签后写入的商户号，backend 可信路径据此构造调用主体。 */
    public static final String HEADER_MERCHANT_ID = "X-Finflow-Merchant-Id";

    private final byte[] expectedToken;

    public InternalAccessGuard(@Value("${finflow.internal.token}") String token) {
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    /** 空值一律视为不匹配；长度不同直接返回 false。 */
    public boolean matches(String token) {
        return token != null
                && MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8));
    }
}
