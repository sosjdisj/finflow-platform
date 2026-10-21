package org.example.finflowbackend.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.finflowbackend.audit.annotation.Audited;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.system.dto.AssignPermissionsRequest;
import org.example.finflowbackend.system.dto.RoleCreateRequest;
import org.example.finflowbackend.system.dto.RolePermissionsResponse;
import org.example.finflowbackend.system.dto.RoleResponse;
import org.example.finflowbackend.system.dto.RoleUpdateRequest;
import org.example.finflowbackend.system.service.RoleService;
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
 * 角色管理接口。权限编码与 sys_permission 初始化数据一一对应。
 */
@RestController
@Tag(name = "角色管理")
@RequestMapping("/api/v1/system/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:role:list')")
    public ApiResponse<PageResponse<RoleResponse>> page(@RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(roleService.page(page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:role:create')")
    public ApiResponse<RoleResponse> create(@Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.ok(roleService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:update')")
    public ApiResponse<RoleResponse> update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        return ApiResponse.ok(roleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role:list')")
    public ApiResponse<RolePermissionsResponse> getPermissions(@PathVariable Long id) {
        return ApiResponse.ok(roleService.getPermissions(id));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role:assign')")
    @Audited(action = "ROLE_PERMISSION_ASSIGN", resourceType = "ROLE", resourceId = "#id")
    public ApiResponse<Void> assignPermissions(@PathVariable Long id,
                                               @Valid @RequestBody AssignPermissionsRequest request) {
        roleService.assignPermissions(id, request);
        return ApiResponse.ok();
    }
}
