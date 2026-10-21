package org.example.finflowbackend.auth.dto;

import org.example.finflowbackend.auth.entity.SysUser;

/** 用户信息响应，不含任何密码字段。 */
public record UserInfoResponse(Long id, String username, String nickname, String email, String phone) {

    public static UserInfoResponse from(SysUser user) {
        return new UserInfoResponse(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(),
                user.getPhone());
    }
}
