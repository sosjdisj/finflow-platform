package org.example.finflowbackend.openapi.web.dto;

import org.example.finflowbackend.openapi.quota.OpenApiQuotaView;

import java.util.List;

/**
 * 网关注入档案（M13）：网关做边缘验签 / 限流所需的全部信息，一次取回。
 * secret 是解密后的明文，只在网关内存里用于 HMAC，不得打印日志、不得出现在普通对外接口。
 * quotas 为每个接口的实际生效阈值（已按平台默认回填），网关不需要知道「0 表示默认」这条规则。
 */
public record OpenApiAccessProfileResponse(Long merchantId, String apiKey, String secret, String ipWhitelist,
                                           String merchantStatus, List<OpenApiQuotaView> quotas) {
}
