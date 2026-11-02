package org.example.finflowbackend.channel.api;

import org.example.finflowbackend.common.api.ErrorCodeSpec;

/**
 * channel 模块错误码（45xxx 段）。
 */
public enum ChannelErrorCode implements ErrorCodeSpec {

    CHANNEL_NOT_FOUND(45001, "该支付方式无可用支付渠道", 409),
    /** M08：渠道服务不可用或已触发熔断降级（调用方按快速失败处理）。 */
    CHANNEL_SERVICE_UNAVAILABLE(45002, "渠道服务不可用，请稍后重试", 503);

    private final int code;
    private final String message;
    private final int httpStatus;

    ChannelErrorCode(int code, String message, int httpStatus) {
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
