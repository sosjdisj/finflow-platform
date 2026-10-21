package org.example.finflowbackend.merchant.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.finflowbackend.audit.annotation.Audited;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.merchant.dto.MerchantRateLogResponse;
import org.example.finflowbackend.merchant.dto.MerchantRateResponse;
import org.example.finflowbackend.merchant.dto.MerchantRateSetRequest;
import org.example.finflowbackend.merchant.service.RateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商户费率接口。
 */
@RestController
@Tag(name = "商户费率")
@RequestMapping("/api/v1/merchants/{merchantId}")
public class RateController {

    private final RateService rateService;

    public RateController(RateService rateService) {
        this.rateService = rateService;
    }

    @GetMapping("/rate")
    @PreAuthorize("hasAuthority('merchant:merchant:rate')")
    public ApiResponse<MerchantRateResponse> get(@PathVariable Long merchantId) {
        return ApiResponse.ok(rateService.get(merchantId));
    }

    @PutMapping("/rate")
    @PreAuthorize("hasAuthority('merchant:merchant:rate')")
    @Audited(action = "MERCHANT_RATE_SET", resourceType = "MERCHANT", resourceId = "#merchantId")
    public ApiResponse<MerchantRateResponse> set(@PathVariable Long merchantId,
                                                 @Valid @RequestBody MerchantRateSetRequest request) {
        return ApiResponse.ok(rateService.set(merchantId, request));
    }

    @GetMapping("/rate-logs")
    @PreAuthorize("hasAuthority('merchant:merchant:rate')")
    public ApiResponse<List<MerchantRateLogResponse>> logs(@PathVariable Long merchantId) {
        return ApiResponse.ok(rateService.logs(merchantId));
    }
}
