package org.example.finflowbackend.auth;

import org.example.finflowbackend.common.api.ErrorCodeSpec;

/**
 * auth 模块错误码（41xxx 段）。
 * 模块业务码定义在模块内，不进公共 ErrorCode 枚举。
 */
public enum AuthErrorCode implements ErrorCodeSpec {

    USERNAME_EXISTS(41001, "用户名已存在", 409),
    LOGIN_FAILED(41002, "用户名或密码错误", 400),
    USER_DISABLED(41003, "账号已被禁用", 403),
    REFRESH_TOKEN_INVALID(41004, "登录凭证已失效，请重新登录", 401),
    OLD_PASSWORD_MISMATCH(41005, "原密码不正确", 400),
    PASSWORD_SAME_AS_OLD(41006, "新密码不能与原密码相同", 400),
    LOGIN_LOCKED(41007, "失败次数过多，账号已锁定，请稍后再试", 429),
    CAPTCHA_INVALID(41008, "验证码错误或已过期", 400),
    USER_CANNOT_DISABLE_SELF(41009, "不能禁用当前登录账号", 400);

    private final int code;
    private final String message;
    private final int httpStatus;

    AuthErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
