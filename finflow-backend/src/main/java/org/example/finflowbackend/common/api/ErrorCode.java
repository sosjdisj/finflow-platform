package org.example.finflowbackend.common.api;

/**
 * 统一错误码枚举。
 * 段位约定：0 成功；4xxxx 客户端/业务错误；5xxxx 系统错误。
 * 各模块后续在业务码段内扩展（如 41xxx），不在这里堆砌。
 */
public enum ErrorCode implements ErrorCodeSpec {

    SUCCESS(0, "成功", 200),
    PARAM_ERROR(40000, "参数错误", 400),
    UNAUTHORIZED(40100, "未认证或凭证已失效", 401),
    FORBIDDEN(40300, "无访问权限", 403),
    NOT_FOUND(40400, "资源不存在", 404),
    CONFLICT(40900, "资源冲突", 409),
    BUSINESS_ERROR(41000, "业务处理失败", 400),
    IDEMPOTENCY_KEY_MISSING(41200, "缺少 Idempotency-Key 请求头", 400),
    IDEMPOTENCY_PROCESSING(41201, "相同幂等键的请求正在处理中，请稍后重试", 409),
    IDEMPOTENCY_MISMATCH(41202, "幂等键与原请求内容不一致", 409),
    SYSTEM_ERROR(50000, "系统内部错误", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    ErrorCode(int code, String message, int httpStatus) {
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
