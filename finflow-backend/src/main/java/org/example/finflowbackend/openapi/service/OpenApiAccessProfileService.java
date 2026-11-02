package org.example.finflowbackend.openapi.service;

import org.example.finflowbackend.merchant.api.ApiKeyApi;
import org.example.finflowbackend.merchant.api.MerchantApi;
import org.example.finflowbackend.merchant.api.MerchantDetailResponse;
import org.example.finflowbackend.merchant.api.OpenApiCredential;
import org.example.finflowbackend.openapi.quota.OpenApiQuotaService;
import org.example.finflowbackend.openapi.web.dto.OpenApiAccessProfileResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 网关注入档案（M13）：把 merchant 的凭证与 openapi 的配额策略组装成一份只读快照。
 * 组装放在这里而不是 controller，保证「凭证 → 商户状态 → 配额」的取数顺序只有一处。
 */
@Service
public class OpenApiAccessProfileService {

    private final ApiKeyApi apiKeyApi;
    private final MerchantApi merchantApi;
    private final OpenApiQuotaService quotaService;

    public OpenApiAccessProfileService(ApiKeyApi apiKeyApi, MerchantApi merchantApi,
                                       OpenApiQuotaService quotaService) {
        this.apiKeyApi = apiKeyApi;
        this.merchantApi = merchantApi;
        this.quotaService = quotaService;
    }

    /** 凭证不存在 / 停用 / 无密文时返回 empty，由调用方按「凭证无效」处理。 */
    public Optional<OpenApiAccessProfileResponse> profile(String apiKey) {
        return apiKeyApi.findCredential(apiKey).map(this::assemble);
    }

    private OpenApiAccessProfileResponse assemble(OpenApiCredential credential) {
        MerchantDetailResponse detail = merchantApi.getDetail(credential.merchantId());
        return new OpenApiAccessProfileResponse(credential.merchantId(), credential.apiKey(), credential.secret(),
                credential.ipWhitelist(), detail.merchant().status(), quotaService.list(credential.merchantId()));
    }
}
