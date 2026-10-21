package org.example.finflowbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 创建用户请求：用户名创建后不可改，初始密码由管理员下发。 */
public record UserCreateRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 64, message = "用户名长度需在 4~64 之间")
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需在 8~64 之间")
        String password,

        @Size(max = 64, message = "显示名最长 64 个字符")
        String nickname,

        @jakarta.validation.constraints.Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱最长 128 个字符")
        String email,

        @Size(max = 32, message = "手机号最长 32 个字符")
        String phone
) {
}
