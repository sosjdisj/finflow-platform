package org.example.finflowbackend.auth.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.finflowbackend.auth.dto.UserAdminResponse;
import org.example.finflowbackend.auth.dto.UserCreateRequest;
import org.example.finflowbackend.auth.dto.UserPasswordRequest;
import org.example.finflowbackend.auth.dto.UserStatusRequest;
import org.example.finflowbackend.auth.dto.UserUpdateRequest;
import org.example.finflowbackend.auth.service.UserAdminService;
import org.example.finflowbackend.auth.web.CurrentUserHolder;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
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
 * 用户管理接口（路由挂 /system/users，与分配角色接口同前缀）。
 * sys_user 读写归 auth 模块；分配角色见 system 模块 UserAssignController。
 */
@RestController
@Tag(name = "用户管理")
@RequestMapping("/api/v1/system/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:list')")
    public ApiResponse<PageResponse<UserAdminResponse>> page(@RequestParam(defaultValue = "1") long page,
                                                             @RequestParam(defaultValue = "10") long size,
                                                             @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(userAdminService.page(page, size, keyword));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:user:create')")
    public ApiResponse<UserAdminResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.ok(userAdminService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    public ApiResponse<UserAdminResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.ok(userAdminService.update(id, request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:user:update')")
    public ApiResponse<UserAdminResponse> updateStatus(@PathVariable Long id,
                                                       @Valid @RequestBody UserStatusRequest request) {
        return ApiResponse.ok(userAdminService.updateStatus(CurrentUserHolder.current().userId(), id, request));
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('system:user:reset-password')")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody UserPasswordRequest request) {
        userAdminService.resetPassword(id, request);
        return ApiResponse.ok();
    }
}
