package org.example.finflowbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 登录请求。验证码经 GET /auth/captcha 获取，必填。 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password,
        @NotBlank(message = "验证码不能为空") String captchaId,
        @NotBlank(message = "验证码不能为空") String captchaCode
) {
}
