package org.example.finflowbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 刷新 / 登出请求（两者都只需 refresh token，共用同一结构）。 */
public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken 不能为空") String refreshToken
) {
}
