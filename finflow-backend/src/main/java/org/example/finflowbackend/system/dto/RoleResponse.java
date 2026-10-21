package org.example.finflowbackend.system.dto;

import org.example.finflowbackend.system.entity.SysRole;

import java.time.LocalDateTime;

/**
 * 角色信息。
 */
public record RoleResponse(Long id, String code, String name, String description,
                           Integer builtIn, Integer status, LocalDateTime createdAt) {

    public static RoleResponse from(SysRole role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                role.getBuiltIn(), role.getStatus(), role.getCreatedAt());
    }
}
