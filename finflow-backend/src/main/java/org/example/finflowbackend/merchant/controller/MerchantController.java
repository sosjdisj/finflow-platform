package org.example.finflowbackend.merchant.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.merchant.dto.MerchantCreateRequest;
import org.example.finflowbackend.merchant.dto.MerchantDetailResponse;
import org.example.finflowbackend.merchant.dto.MerchantResponse;
import org.example.finflowbackend.merchant.dto.MerchantReviewRequest;
import org.example.finflowbackend.merchant.dto.MerchantStatusChangeRequest;
import org.example.finflowbackend.merchant.dto.MerchantUpdateRequest;
import org.example.finflowbackend.merchant.service.MerchantService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商户管理接口。权限编码与 sys_permission 初始化数据一一对应。
 */
@RestController
@Tag(name = "商户")
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('merchant:merchant:create')")
    public ApiResponse<MerchantResponse> create(@Valid @RequestBody MerchantCreateRequest request) {
        return ApiResponse.ok(merchantService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('merchant:merchant:list')")
    public ApiResponse<PageResponse<MerchantResponse>> page(@RequestParam(defaultValue = "1") long page,
                                                            @RequestParam(defaultValue = "20") long size,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(merchantService.page(page, size, status, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('merchant:merchant:list')")
    public ApiResponse<MerchantDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(merchantService.getDetail(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('merchant:merchant:update')")
    public ApiResponse<MerchantResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody MerchantUpdateRequest request) {
        return ApiResponse.ok(merchantService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('merchant:merchant:status')")
    public ApiResponse<MerchantResponse> changeStatus(@PathVariable Long id,
                                                      @Valid @RequestBody MerchantStatusChangeRequest request) {
        return ApiResponse.ok(merchantService.changeStatus(id, request));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('merchant:merchant:review')")
    public ApiResponse<MerchantResponse> review(@PathVariable Long id,
                                                @Valid @RequestBody MerchantReviewRequest request) {
        return ApiResponse.ok(merchantService.review(id, request));
    }
}
