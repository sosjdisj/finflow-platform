package org.example.finflowbackend.notification;

import org.example.finflowbackend.common.api.ErrorCodeSpec;

/**
 * notification 模块错误码（52xxx 段）。
 */
public enum NotificationErrorCode implements ErrorCodeSpec {

    TEMPLATE_NOT_FOUND(52001, "通知模板不存在或未启用", 404),

    NOTIFICATION_NOT_FOUND(52002, "通知不存在", 404),

    /** M16：通知服务不可用或已触发熔断降级（管理端按快速失败提示）。 */
    NOTIFICATION_SERVICE_UNAVAILABLE(52003, "通知服务不可用，请稍后重试", 503);

    private final int code;
    private final String message;
    private final int httpStatus;

    NotificationErrorCode(int code, String message, int httpStatus) {
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
