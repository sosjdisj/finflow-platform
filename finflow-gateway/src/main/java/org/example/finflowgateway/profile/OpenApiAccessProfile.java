package org.example.finflowgateway.profile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 网关注入档案（M13）：backend 内部接口返回的验签 + 限流所需信息。
 * secret 只在网关内存里用于 HMAC，禁止进日志、禁止回写商户。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenApiAccessProfile(Long merchantId, String apiKey, String secret, String ipWhitelist,
                                   String merchantStatus, List<QuotaPolicy> quotas) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuotaPolicy(String apiCode, boolean enabled, int effectiveQpsLimit, long effectiveDailyLimit) {
    }
}
