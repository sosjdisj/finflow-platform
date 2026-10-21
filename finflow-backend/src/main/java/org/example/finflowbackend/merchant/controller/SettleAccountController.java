package org.example.finflowbackend.merchant.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.merchant.dto.SettleAccountCreateRequest;
import org.example.finflowbackend.merchant.dto.SettleAccountResponse;
import org.example.finflowbackend.merchant.service.SettleAccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商户结算账户接口：信息管理，不动真实资金。
 */
@RestController
@Tag(name = "结算账户")
@RequestMapping("/api/v1/merchants/{merchantId}/settle-accounts")
public class SettleAccountController {

    private final SettleAccountService settleAccountService;

    public SettleAccountController(SettleAccountService settleAccountService) {
        this.settleAccountService = settleAccountService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('merchant:merchant:settle-account')")
    public ApiResponse<SettleAccountResponse> create(@PathVariable Long merchantId,
                                                     @Valid @RequestBody SettleAccountCreateRequest request) {
        return ApiResponse.ok(settleAccountService.create(merchantId, request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('merchant:merchant:settle-account')")
    public ApiResponse<List<SettleAccountResponse>> list(@PathVariable Long merchantId) {
        return ApiResponse.ok(settleAccountService.list(merchantId));
    }

    @DeleteMapping("/{accountId}")
    @PreAuthorize("hasAuthority('merchant:merchant:settle-account')")
    public ApiResponse<Void> delete(@PathVariable Long merchantId, @PathVariable Long accountId) {
        settleAccountService.delete(merchantId, accountId);
        return ApiResponse.ok();
    }

    @PutMapping("/{accountId}/default")
    @PreAuthorize("hasAuthority('merchant:merchant:settle-account')")
    public ApiResponse<SettleAccountResponse> markDefault(@PathVariable Long merchantId,
                                                          @PathVariable Long accountId) {
        return ApiResponse.ok(settleAccountService.markDefault(merchantId, accountId));
    }
}
