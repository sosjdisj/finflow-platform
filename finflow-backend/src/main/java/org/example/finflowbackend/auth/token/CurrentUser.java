package org.example.finflowbackend.auth.token;

/**
 * 令牌解析出的登录主体：仅放鉴权必需的最小信息，不放密码等敏感字段。
 */
public record CurrentUser(Long userId, String username) {
}
