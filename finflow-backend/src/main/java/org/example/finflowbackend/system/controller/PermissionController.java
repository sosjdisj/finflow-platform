package org.example.finflowbackend.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.system.dto.PermissionCreateRequest;
import org.example.finflowbackend.system.dto.PermissionResponse;
import org.example.finflowbackend.system.dto.PermissionUpdateRequest;
import org.example.finflowbackend.system.service.PermissionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限点管理接口。
 */
@RestController
@Tag(name = "权限管理")
@RequestMapping("/api/v1/system/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:permission:list')")
    public ApiResponse<PageResponse<PermissionResponse>> page(@RequestParam(defaultValue = "1") long page,
                                                              @RequestParam(defaultValue = "50") long size) {
        return ApiResponse.ok(permissionService.page(page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:permission:create')")
    public ApiResponse<PermissionResponse> create(@Valid @RequestBody PermissionCreateRequest request) {
        return ApiResponse.ok(permissionService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:permission:update')")
    public ApiResponse<PermissionResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody PermissionUpdateRequest request) {
        return ApiResponse.ok(permissionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:permission:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return ApiResponse.ok();
    }
}
