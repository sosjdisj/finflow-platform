package org.example.finflowbackend.openapi;

import org.example.finflowbackend.common.api.ErrorCode;
import org.example.finflowbackend.openapi.api.OpenApiErrorCode;
import org.example.finflowbackend.openapi.api.OpenApiErrorMapper;
import org.example.finflowbackend.payment.PaymentErrorCode;
import org.example.finflowbackend.refund.api.RefundErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * M12 错误码归一单测：对外只暴露 47xxx，内部模块码段不外泄；
 * 区分「入参 / 找不到 / 状态冲突 / 系统错误」四类语义即可，不逐码穷举。
 */
class OpenApiErrorMapperTest {

    @Test
    void openApiErrorPassesThrough() {
        assertEquals(OpenApiErrorCode.NOT_FOUND, OpenApiErrorMapper.map(OpenApiErrorCode.NOT_FOUND));
    }

    @Test
    void systemErrorsBecomeSystemError() {
        assertEquals(OpenApiErrorCode.SYSTEM_ERROR, OpenApiErrorMapper.map(ErrorCode.SYSTEM_ERROR));
    }

    @Test
    void notFoundFamilyMapsToNotFound() {
        assertEquals(OpenApiErrorCode.NOT_FOUND, OpenApiErrorMapper.map(PaymentErrorCode.ORDER_NOT_FOUND));
        assertEquals(OpenApiErrorCode.NOT_FOUND, OpenApiErrorMapper.map(RefundErrorCode.REFUND_NOT_FOUND));
        assertEquals(OpenApiErrorCode.NOT_FOUND, OpenApiErrorMapper.map(ErrorCode.NOT_FOUND));
    }

    @Test
    void illegalTransitionMapsToConflict() {
        assertEquals(OpenApiErrorCode.CONFLICT,
                OpenApiErrorMapper.map(PaymentErrorCode.ILLEGAL_STATUS_TRANSITION));
        assertEquals(OpenApiErrorCode.CONFLICT, OpenApiErrorMapper.map(RefundErrorCode.REFUND_AMOUNT_EXCEED));
    }

    @Test
    void invalidInputMapsToParamError() {
        assertEquals(OpenApiErrorCode.PARAM_ERROR, OpenApiErrorMapper.map(ErrorCode.PARAM_ERROR));
        assertEquals(OpenApiErrorCode.PARAM_ERROR, OpenApiErrorMapper.map(PaymentErrorCode.EXPIRED_AT_INVALID));
        assertEquals(OpenApiErrorCode.PARAM_ERROR, OpenApiErrorMapper.map(PaymentErrorCode.AMOUNT_OVER_LIMIT));
        assertEquals(OpenApiErrorCode.PARAM_ERROR, OpenApiErrorMapper.map(RefundErrorCode.REFUND_AMOUNT_INVALID));
    }

    @Test
    void remainingBusinessCodesMapToBusinessFailed() {
        assertEquals(OpenApiErrorCode.BUSINESS_FAILED, OpenApiErrorMapper.map(PaymentErrorCode.RISK_REJECTED));
    }
}
