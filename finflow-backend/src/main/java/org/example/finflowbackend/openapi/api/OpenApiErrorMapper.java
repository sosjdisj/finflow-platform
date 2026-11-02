package org.example.finflowbackend.openapi.api;

import org.example.finflowbackend.common.api.ErrorCodeSpec;

/**
 * 内部错误码 → OpenAPI 对外错误码归一（M12）。
 * 规则只有三类：入参不合法归 PARAM_ERROR；资源/状态类归 NOT_FOUND / CONFLICT；其余业务失败归
 * BUSINESS_FAILED，系统异常归 SYSTEM_ERROR。message 透传原始业务说明，商户能看懂原因，
 * 但看不到内部码段，避免商户按内部码做适配。
 */
public final class OpenApiErrorMapper {

    private OpenApiErrorMapper() {
    }

    public static OpenApiErrorCode map(ErrorCodeSpec errorCode) {
        if (errorCode instanceof OpenApiErrorCode openApiError) {
            return openApiError;
        }
        int code = errorCode.getCode();
        if (code >= 50000) {
            return OpenApiErrorCode.SYSTEM_ERROR;
        }
        return switch (code) {
            case 40000, 41200, 44004, 44008, 46004 -> OpenApiErrorCode.PARAM_ERROR;
            case 40100 -> OpenApiErrorCode.AUTH_FAILED;
            case 40300 -> OpenApiErrorCode.ACCESS_DENIED;
            case 40400, 43001, 44001, 46001 -> OpenApiErrorCode.NOT_FOUND;
            case 40900, 41201, 41202, 43003, 43004, 43005,
                 44002, 44003, 44007,
                 46002, 46003, 46005, 46006 -> OpenApiErrorCode.CONFLICT;
            default -> OpenApiErrorCode.BUSINESS_FAILED;
        };
    }
}
