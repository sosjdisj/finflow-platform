package org.example.finflowbackend.settlement;

import org.example.finflowbackend.common.api.ErrorCodeSpec;

/**
 * settlement 模块错误码（44xxx 段，沿模块分段约定）。
 */
public enum SettlementErrorCode implements ErrorCodeSpec {

    ILLEGAL_STATUS_TRANSITION(44001, "非法的结算单状态转换", 409),
    SETTLEMENT_NOT_FOUND(44002, "结算单不存在", 404);

    private final int code;
    private final String message;
    private final int httpStatus;

    SettlementErrorCode(int code, String message, int httpStatus) {
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
