package org.example.finflowbackend.auth.dto;

import jakarta.validation.constraints.Size;

/** 编辑用户请求：用户名与状态不在此修改（状态走启停接口）。 */
public record UserUpdateRequest(
        @Size(max = 64, message = "显示名最长 64 个字符")
        String nickname,

        @jakarta.validation.constraints.Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱最长 128 个字符")
        String email,

        @Size(max = 32, message = "手机号最长 32 个字符")
        String phone
) {
}
