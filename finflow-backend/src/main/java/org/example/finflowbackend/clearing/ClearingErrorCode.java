package org.example.finflowbackend.clearing;

import org.example.finflowbackend.common.api.ErrorCodeSpec;

/**
 * clearing 模块错误码（47xxx 段，沿模块分段约定）。
 */
public enum ClearingErrorCode implements ErrorCodeSpec {

    CLEARING_NOT_FOUND(47001, "清分单不存在", 404);

    private final int code;
    private final String message;
    private final int httpStatus;

    ClearingErrorCode(int code, String message, int httpStatus) {
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
