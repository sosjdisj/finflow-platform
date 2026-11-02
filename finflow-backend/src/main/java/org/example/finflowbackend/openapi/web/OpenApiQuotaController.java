package org.example.finflowbackend.openapi.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.openapi.quota.OpenApiQuotaService;
import org.example.finflowbackend.openapi.quota.OpenApiQuotaView;
import org.example.finflowbackend.openapi.web.dto.OpenApiQuotaUpdateRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OpenAPI 配额管理（M12，运营后台）：按商户 + 接口维护 QPS / 每日配额与启停，
 * 计数在调用时由 {@link OpenApiQuotaService} 实时执行，本接口只维护策略。
 */
@RestController
@Tag(name = "OpenAPI 配额")
@RequestMapping("/api/v1/merchants/{merchantId}/openapi-quotas")
public class OpenApiQuotaController {

    private final OpenApiQuotaService quotaService;

    public OpenApiQuotaController(OpenApiQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('merchant:merchant:api-key')")
    public ApiResponse<List<OpenApiQuotaView>> list(@PathVariable Long merchantId) {
        return ApiResponse.ok(quotaService.list(merchantId));
    }

    @PutMapping("/{apiCode}")
    @PreAuthorize("hasAuthority('merchant:merchant:api-key')")
    public ApiResponse<OpenApiQuotaView> update(@PathVariable Long merchantId, @PathVariable String apiCode,
                                                @Valid @RequestBody OpenApiQuotaUpdateRequest request) {
        return ApiResponse.ok(quotaService.update(merchantId, apiCode, request.qpsLimit(),
                request.dailyLimit(), request.enabled()));
    }
}
