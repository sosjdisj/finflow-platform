package org.example.finflowbackend.common.exception;

import org.example.finflowbackend.common.api.ErrorCodeSpec;

/**
 * 业务异常：由业务代码主动抛出，全局异常处理器负责转成统一响应。
 * 接受任意 {@link ErrorCodeSpec}，模块可以定义自己的错误码枚举。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCodeSpec errorCode;

    public BusinessException(ErrorCodeSpec errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCodeSpec errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCodeSpec getErrorCode() {
        return errorCode;
    }
}
