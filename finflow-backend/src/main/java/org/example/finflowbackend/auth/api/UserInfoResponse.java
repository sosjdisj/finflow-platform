package org.example.finflowbackend.auth.api;

import org.example.finflowbackend.auth.entity.SysUser;

/**
 * 用户信息响应（M03 facade），不含任何密码字段。
 * 由 auth 模块内部从实体转换后经 {@link AuthApi} 暴露给其他模块。
 */
public record UserInfoResponse(Long id, String username, String nickname, String email, String phone) {

    public static UserInfoResponse from(SysUser user) {
        return new UserInfoResponse(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(),
                user.getPhone());
    }
}
