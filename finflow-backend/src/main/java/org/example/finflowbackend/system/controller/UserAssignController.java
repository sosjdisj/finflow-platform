package org.example.finflowbackend.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.finflowbackend.audit.annotation.Audited;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.system.dto.AssignRolesRequest;
import org.example.finflowbackend.system.dto.UserRolesResponse;
import org.example.finflowbackend.system.service.UserAssignService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户角色分配接口。
 */
@RestController
@Tag(name = "用户角色分配")
@RequestMapping("/api/v1/system/users")
public class UserAssignController {

    private final UserAssignService userAssignService;

    public UserAssignController(UserAssignService userAssignService) {
        this.userAssignService = userAssignService;
    }

    @GetMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('system:user:assign-role')")
    public ApiResponse<UserRolesResponse> getUserRoles(@PathVariable Long userId) {
        return ApiResponse.ok(userAssignService.getUserRoles(userId));
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAuthority('system:user:assign-role')")
    @Audited(action = "USER_ROLE_ASSIGN", resourceType = "ROLE", resourceId = "#userId")
    public ApiResponse<Void> assignRoles(@PathVariable Long userId,
                                         @Valid @RequestBody AssignRolesRequest request) {
        userAssignService.assignRoles(userId, request);
        return ApiResponse.ok();
    }
}
