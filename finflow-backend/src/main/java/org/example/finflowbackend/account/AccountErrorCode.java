package org.example.finflowbackend.account;

import org.example.finflowbackend.common.api.ErrorCodeSpec;

/**
 * account 模块错误码（45xxx 段）。
 */
public enum AccountErrorCode implements ErrorCodeSpec {

    AMOUNT_INVALID(45001, "金额必须为正数", 400),
    ACCOUNT_NOT_FOUND(45002, "账户不存在", 404),
    ACCOUNT_INSUFFICIENT_BALANCE(45003, "可用余额不足", 409),
    INSUFFICIENT_FROZEN(45004, "冻结余额不足", 409),
    ACCOUNT_CONCURRENT_MODIFY(45005, "账户并发修改冲突，请重试", 409),
    ADJUST_AMOUNT_ZERO(45006, "调账金额不能为 0", 400);

    private final int code;
    private final String message;
    private final int httpStatus;

    AccountErrorCode(int code, String message, int httpStatus) {
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
