package org.example.finflowbackend.risk;

import org.example.finflowbackend.common.api.ErrorCodeSpec;

/**
 * risk 模块错误码（49xxx 段）。
 */
public enum RiskErrorCode implements ErrorCodeSpec {

    RULE_NOT_FOUND(49001, "风控规则不存在", 404),
    RULE_TYPE_INVALID(49002, "风控规则类型不合法", 400),
    RULE_PARAMS_INVALID(49003, "规则参数与所选规则类型不符", 400),
    RULE_NAME_EXISTS(49004, "规则名称已存在", 409),
    BLACKLIST_DUPLICATE(49005, "该黑名单记录已存在", 409),
    ORDER_NOT_IN_REVIEW(49006, "订单不在风控待审状态", 409),
    BLACKLIST_NOT_FOUND(49007, "黑名单记录不存在", 404);

    private final int code;
    private final String message;
    private final int httpStatus;

    RiskErrorCode(int code, String message, int httpStatus) {
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
