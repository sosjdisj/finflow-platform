package org.example.finflowbackend.merchant;

import org.example.finflowbackend.common.api.ErrorCodeSpec;

/**
 * merchant 模块错误码（43xxx 段）。
 */
public enum MerchantErrorCode implements ErrorCodeSpec {

    MERCHANT_NOT_FOUND(43001, "商户不存在", 404),
    MERCHANT_NAME_EXISTS(43002, "商户名称已存在", 409),
    ILLEGAL_STATUS_TRANSITION(43003, "非法的商户状态转换", 409),
    MERCHANT_CLOSED(43004, "商户已关闭，禁止修改", 409),
    MERCHANT_NOT_IN_REVIEW(43005, "商户不在审核中状态，无法执行审核", 409),
    API_KEY_NOT_FOUND(43006, "API 凭证不存在", 404),
    RATE_REQUIRED(43007, "比例费率与固定手续费至少启用一种", 400),
    SETTLE_ACCOUNT_NOT_FOUND(43008, "结算账户不存在", 404),
    BANK_NAME_REQUIRED(43009, "银行卡账户必须填写开户行", 400);

    private final int code;
    private final String message;
    private final int httpStatus;

    MerchantErrorCode(int code, String message, int httpStatus) {
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
