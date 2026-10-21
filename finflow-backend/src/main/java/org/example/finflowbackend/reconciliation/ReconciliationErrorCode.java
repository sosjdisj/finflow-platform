package org.example.finflowbackend.reconciliation;

import org.example.finflowbackend.common.api.ErrorCodeSpec;

/**
 * reconciliation 模块错误码（48xxx 段，沿模块分段约定）。
 */
public enum ReconciliationErrorCode implements ErrorCodeSpec {

    TASK_NOT_FOUND(48001, "对账任务不存在", 404),
    BILL_UPLOADED(48002, "该渠道该日期账单已上传", 409),
    BILL_FILE_INVALID(48003, "账单文件为空或格式非法", 400),
    BILL_ROW_INVALID(48004, "账单行格式非法", 400),
    ILLEGAL_STATUS_TRANSITION(48005, "非法的对账任务状态转换", 409),
    CHANNEL_NOT_FOUND(48006, "渠道不存在或未启用", 404),
    TASK_NOT_MATCHABLE(48007, "对账任务状态不允许匹配", 409),
    RECORD_NOT_FOUND(48008, "对账匹配记录不存在", 404),
    RECORD_NOT_DIFFERENCE(48009, "仅匹配异常记录可发起差异处理", 400),
    DIFFERENCE_NOT_FOUND(48010, "差异单不存在", 404),
    DIFFERENCE_OPEN_EXISTS(48011, "该记录已存在未关闭的差异单", 409),
    DIFFERENCE_STATUS_INVALID(48012, "差异单状态不允许该操作", 409),
    DIFFERENCE_HANDLE_INVALID(48013, "差异处理参数与所选处理方式不符", 400);

    private final int code;
    private final String message;
    private final int httpStatus;

    ReconciliationErrorCode(int code, String message, int httpStatus) {
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
