package org.example.finflowbackend.risk.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.finflowbackend.auth.web.CurrentUserHolder;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.risk.dto.BlacklistCreateRequest;
import org.example.finflowbackend.risk.entity.RiskBlacklist;
import org.example.finflowbackend.risk.service.RiskBlacklistService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 黑名单管理接口。权限编码与 V27 初始化数据一一对应。
 */
@RestController
@Tag(name = "风控黑名单")
@RequestMapping("/api/v1/risk/blacklists")
public class RiskBlacklistController {

    private final RiskBlacklistService blacklistService;

    public RiskBlacklistController(RiskBlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('risk:blacklist:list')")
    public ApiResponse<PageResponse<RiskBlacklist>> page(@RequestParam(defaultValue = "1") long page,
                                                         @RequestParam(defaultValue = "20") long size,
                                                         @RequestParam(required = false) String dimension,
                                                         @RequestParam(required = false) String value) {
        return ApiResponse.ok(blacklistService.page(page, size, dimension, value));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('risk:blacklist:create')")
    public ApiResponse<RiskBlacklist> create(@Valid @RequestBody BlacklistCreateRequest request) {
        RiskBlacklist entry = new RiskBlacklist();
        entry.setDimension(request.dimension());
        entry.setValue(request.value());
        entry.setReason(request.reason());
        return ApiResponse.ok(blacklistService.create(entry, CurrentUserHolder.current().username()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('risk:blacklist:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        blacklistService.delete(id);
        return ApiResponse.ok(null);
    }
}
