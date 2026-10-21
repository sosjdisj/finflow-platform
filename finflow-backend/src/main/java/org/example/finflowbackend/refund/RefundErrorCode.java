package org.example.finflowbackend.refund;

import org.example.finflowbackend.common.api.ErrorCodeSpec;

/**
 * refund 模块错误码（46xxx 段）。
 */
public enum RefundErrorCode implements ErrorCodeSpec {

    REFUND_NOT_FOUND(46001, "退款单不存在", 404),
    ORDER_NOT_REFUNDABLE(46002, "订单当前状态不可退款", 409),
    REFUND_AMOUNT_EXCEED(46003, "退款金额超过剩余可退金额", 409),
    REFUND_AMOUNT_INVALID(46004, "退款金额必须为正数", 400),
    ILLEGAL_STATUS_TRANSITION(46005, "非法的退款单状态转换", 409),
    CHANNEL_REFUND_REJECTED(46006, "渠道拒绝退款受理", 409);

    private final int code;
    private final String message;
    private final int httpStatus;

    RefundErrorCode(int code, String message, int httpStatus) {
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