package org.example.finflowbackend.payment;

import org.example.finflowbackend.common.api.ErrorCodeSpec;

/**
 * payment 模块错误码（44xxx 段）。
 */
public enum PaymentErrorCode implements ErrorCodeSpec {

    ORDER_NOT_FOUND(44001, "支付订单不存在", 404),
    MERCHANT_NOT_ACTIVE(44002, "商户非生效状态，不能发起支付", 409),
    ILLEGAL_STATUS_TRANSITION(44003, "非法的支付订单状态转换", 409),
    EXPIRED_AT_INVALID(44004, "订单过期时间必须晚于当前时间", 400),
    CHANNEL_NOT_FOUND(44005, "该支付方式无可用支付渠道", 409),
    RISK_REJECTED(44006, "交易被风控拦截", 403),
    SWITCH_OFF(44007, "支付下单开关已关闭，暂不接受新订单", 409),
    AMOUNT_OVER_LIMIT(44008, "订单金额超过全局单笔限额", 400);

    private final int code;
    private final String message;
    private final int httpStatus;

    PaymentErrorCode(int code, String message, int httpStatus) {
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
