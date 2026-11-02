package org.example.finflowgateway.api;

/**
 * 网关对外错误码（M13）：与 backend 的 OpenApiErrorCode 保持同一 47xxx 段与 HTTP 状态。
 * 网关只产出「接入层」能判断的错误（缺头 / 凭证 / 时间戳 / IP / nonce / 配额 / 商户状态 / 系统繁忙），
 * 业务错误由 backend 原样透传，网关不重写。
 */
public enum GatewayErrorCode {

    PARAM_ERROR(47001, "参数错误", 400),
    AUTH_FAILED(47002, "签名校验失败", 401),
    TIMESTAMP_EXPIRED(47003, "请求时间戳超出允许窗口", 401),
    NONCE_REPLAYED(47004, "请求重复提交（nonce 已使用）", 409),
    CREDENTIAL_INVALID(47005, "API 凭证无效或已停用", 401),
    IP_NOT_ALLOWED(47006, "请求 IP 不在白名单内", 403),
    MERCHANT_UNAVAILABLE(47007, "商户状态不可用", 403),
    QUOTA_EXCEEDED(47008, "请求超出接口配额", 429),
    OPENAPI_DISABLED(47009, "该接口未开通或已停用", 403),
    SYSTEM_ERROR(47019, "系统繁忙，请稍后重试", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    GatewayErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
