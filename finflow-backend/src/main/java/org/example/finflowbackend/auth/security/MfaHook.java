package org.example.finflowbackend.auth.security;

import org.example.finflowbackend.auth.entity.SysUser;

/**
 * MFA 扩展点（S06 仅预留，不实现）：未来接入多因素认证时实现本接口并注册为 Spring Bean 即可。
 * 登录流程在密码校验通过后调用 {@link #allowLogin}，返回 false 按登录失败处理。
 * 未注册实现时登录直接放行。
 */
public interface MfaHook {

    boolean allowLogin(SysUser user, String ip);
}
