package org.example.finflowbackend.auth.web;

import org.example.finflowbackend.auth.token.CurrentUser;
import org.example.finflowbackend.common.api.ErrorCode;
import org.example.finflowbackend.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户上下文：从 SecurityContext 读取由 JwtAuthenticationFilter 写入的登录主体。
 * 业务代码只读；未登录时抛统一 401 业务异常。
 */
public final class CurrentUserHolder {

    private CurrentUserHolder() {
    }

    public static CurrentUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
            return user;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
    }
}
