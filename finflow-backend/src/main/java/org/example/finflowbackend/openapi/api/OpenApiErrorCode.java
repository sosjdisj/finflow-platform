package org.example.finflowbackend.openapi.api;

import org.example.finflowbackend.common.api.ErrorCodeSpec;

/**
 * 商户 OpenAPI 对外错误码（M12，47xxx 段）。
 * 对外契约只认这一组码：后台内部码段（43/44/45/46xxx）一律由
 * {@link OpenApiErrorMapper} 归一，商户端不感知平台内部模块划分。
 */
public enum OpenApiErrorCode implements ErrorCodeSpec {

    PARAM_ERROR(47001, "参数错误", 400),
    AUTH_FAILED(47002, "签名校验失败", 401),
    TIMESTAMP_EXPIRED(47003, "请求时间戳超出允许窗口", 401),
    NONCE_REPLAYED(47004, "请求重复提交（nonce 已使用）", 409),
    CREDENTIAL_INVALID(47005, "API 凭证无效或已停用", 401),
    IP_NOT_ALLOWED(47006, "请求 IP 不在白名单内", 403),
    MERCHANT_UNAVAILABLE(47007, "商户状态不可用", 403),
    QUOTA_EXCEEDED(47008, "请求超出接口配额", 429),
    OPENAPI_DISABLED(47009, "该接口未开通或已停用", 403),
    NOT_FOUND(47010, "资源不存在", 404),
    CONFLICT(47011, "业务状态冲突", 409),
    ACCESS_DENIED(47012, "无访问权限", 403),
    BUSINESS_FAILED(47013, "业务处理失败", 400),
    SYSTEM_ERROR(47019, "系统繁忙，请稍后重试", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    OpenApiErrorCode(int code, String message, int httpStatus) {
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
