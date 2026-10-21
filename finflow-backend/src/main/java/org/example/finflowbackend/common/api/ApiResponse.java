package org.example.finflowbackend.common.api;

import org.example.finflowbackend.common.trace.TraceContext;

/**
 * 统一响应包装：code / message / data / traceId。
 * 所有接口一律返回该结构，禁止各接口自造返回格式。
 * traceId 由 {@link org.example.finflowbackend.common.trace.TraceIdFilter} 放入 MDC，
 * 此处统一读取，业务代码无感知；与日志里的 traceId 同源，便于按响应反查日志。
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data, TraceContext.current());
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(ErrorCodeSpec errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null, TraceContext.current());
    }

    public static <T> ApiResponse<T> fail(ErrorCodeSpec errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null, TraceContext.current());
    }
}
