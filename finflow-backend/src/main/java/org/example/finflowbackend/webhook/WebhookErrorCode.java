package org.example.finflowbackend.webhook;

import org.example.finflowbackend.common.api.ErrorCodeSpec;

/**
 * webhook 模块错误码（50xxx 段）。
 */
public enum WebhookErrorCode implements ErrorCodeSpec {

    EVENT_NOT_FOUND(50001, "Webhook 事件不存在", 404),
    EVENT_NOT_RETRYABLE(50002, "仅未成功的事件可手动重试", 409),
    /** M16：通知服务不可用或已触发熔断降级（管理端按快速失败提示）。 */
    WEBHOOK_SERVICE_UNAVAILABLE(50003, "通知服务不可用，请稍后重试", 503);

    private final int code;
    private final String message;
    private final int httpStatus;

    WebhookErrorCode(int code, String message, int httpStatus) {
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
