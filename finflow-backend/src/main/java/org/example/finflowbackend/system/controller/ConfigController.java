package org.example.finflowbackend.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.finflowbackend.audit.annotation.Audited;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.system.dto.ConfigResponse;
import org.example.finflowbackend.system.dto.ConfigUpdateRequest;
import org.example.finflowbackend.system.service.ConfigAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 系统配置管理接口（S37）：key 集合固定（ConfigKeys），仅支持查与改。 */
@RestController
@Tag(name = "系统配置")
@RequestMapping("/api/v1/system/configs")
public class ConfigController {

    private final ConfigAdminService configAdminService;

    public ConfigController(ConfigAdminService configAdminService) {
        this.configAdminService = configAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:config:list')")
    public ApiResponse<List<ConfigResponse>> list() {
        return ApiResponse.ok(configAdminService.listAll());
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('system:config:update')")
    @Audited(action = "CONFIG_UPDATE", resourceType = "SYSTEM_CONFIG", resourceId = "#key")
    public ApiResponse<ConfigResponse> update(@PathVariable String key,
                                              @Valid @RequestBody ConfigUpdateRequest request) {
        return ApiResponse.ok(configAdminService.update(key, request));
    }
}
