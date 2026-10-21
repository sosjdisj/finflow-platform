package org.example.finflowbackend.system.dto;

import java.util.List;

/**
 * 用户已分配的角色。
 */
public record UserRolesResponse(Long userId, List<Long> roleIds) {
}
