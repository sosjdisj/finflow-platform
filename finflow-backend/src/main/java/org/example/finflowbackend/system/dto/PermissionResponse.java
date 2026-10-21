package org.example.finflowbackend.system.dto;

import org.example.finflowbackend.system.entity.SysPermission;

import java.time.LocalDateTime;

/**
 * 权限点信息。
 */
public record PermissionResponse(Long id, String code, String name, Integer type,
                                 String description, Integer status, LocalDateTime createdAt) {

    public static PermissionResponse from(SysPermission permission) {
        return new PermissionResponse(permission.getId(), permission.getCode(), permission.getName(),
                permission.getType(), permission.getDescription(), permission.getStatus(), permission.getCreatedAt());
    }
}
