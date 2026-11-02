package org.example.finflowbackend.common.context;

/**
 * 令牌解析出的登录主体：仅放鉴权必需的最小信息，不放密码等敏感字段。
 * 跨模块共享的登录上下文（M03 facade），不归属某一业务模块。
 */
public record CurrentUser(Long userId, String username) {
}
