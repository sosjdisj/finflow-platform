package org.example.finflowbackend.auth.dto;

import org.example.finflowbackend.auth.entity.SysUser;

import java.time.LocalDateTime;

/** 用户管理列表/详情视图（不含密码哈希）。 */
public record UserAdminResponse(Long id, String username, String nickname, String email, String phone,
                                Integer status, LocalDateTime lastLoginAt, LocalDateTime createdAt) {

    public static UserAdminResponse from(SysUser user) {
        return new UserAdminResponse(user.getId(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.getPhone(), user.getStatus(), user.getLastLoginAt(), user.getCreatedAt());
    }
}
