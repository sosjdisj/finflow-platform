package org.example.finflowbackend.system.dto;

import java.util.List;

/**
 * 角色已分配的权限。
 */
public record RolePermissionsResponse(Long roleId, List<Long> permissionIds) {
}
