package org.example.finflowbackend.merchant.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.merchant.dto.ApiKeyCreateResponse;
import org.example.finflowbackend.merchant.dto.IpWhitelistUpdateRequest;
import org.example.finflowbackend.merchant.dto.MerchantApiKeyResponse;
import org.example.finflowbackend.merchant.service.ApiKeyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商户 API 凭证接口。Secret 明文仅在创建响应中出现一次。
 */
@RestController
@Tag(name = "API 凭证")
@RequestMapping("/api/v1/merchants/{merchantId}/api-keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('merchant:merchant:api-key')")
    public ApiResponse<ApiKeyCreateResponse> create(@PathVariable Long merchantId) {
        return ApiResponse.ok(apiKeyService.create(merchantId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('merchant:merchant:api-key')")
    public ApiResponse<List<MerchantApiKeyResponse>> list(@PathVariable Long merchantId) {
        return ApiResponse.ok(apiKeyService.list(merchantId));
    }

    @PutMapping("/{keyId}/ip-whitelist")
    @PreAuthorize("hasAuthority('merchant:merchant:api-key')")
    public ApiResponse<MerchantApiKeyResponse> updateIpWhitelist(@PathVariable Long merchantId,
                                                                 @PathVariable Long keyId,
                                                                 @Valid @RequestBody IpWhitelistUpdateRequest request) {
        return ApiResponse.ok(apiKeyService.updateIpWhitelist(merchantId, keyId, request));
    }
}
