package org.example.finflowbackend.openapi.web;

import jakarta.validation.ConstraintViolationException;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.openapi.api.OpenApiErrorCode;
import org.example.finflowbackend.openapi.api.OpenApiErrorMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 商户 OpenAPI 异常出口（M12）：只作用于 {@link OpenApiController}，
 * 把内部异常统一归一为 47xxx 对外错误码，商户端看不到平台内部码段。
 *
 * <p>优先级最高，避免落到通用 {@code GlobalExceptionHandler} 输出内部码；
 * 后台配额的 {@link OpenApiQuotaController} 仍走通用处理器，不受影响。</p>
 */
@RestControllerAdvice(assignableTypes = OpenApiController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OpenApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OpenApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        OpenApiErrorCode errorCode = OpenApiErrorMapper.map(e.getErrorCode());
        log.warn("[OpenAPI] 业务异常: internal={}, external={}, message={}",
                e.getErrorCode().getCode(), errorCode.getCode(), e.getMessage());
        // 系统错误不透传内部说明，其余业务说明原样给商户（便于自助排查）
        String message = errorCode == OpenApiErrorCode.SYSTEM_ERROR ? errorCode.getMessage() : e.getMessage();
        return fail(errorCode, message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyInvalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .findFirst()
                .orElse(OpenApiErrorCode.PARAM_ERROR.getMessage());
        return fail(OpenApiErrorCode.PARAM_ERROR, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParamInvalid(HandlerMethodValidationException e) {
        return fail(OpenApiErrorCode.PARAM_ERROR, OpenApiErrorCode.PARAM_ERROR.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .findFirst()
                .orElse(OpenApiErrorCode.PARAM_ERROR.getMessage());
        return fail(OpenApiErrorCode.PARAM_ERROR, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        return fail(OpenApiErrorCode.PARAM_ERROR, "请求体格式错误");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
        return fail(OpenApiErrorCode.NOT_FOUND, OpenApiErrorCode.NOT_FOUND.getMessage());
    }

    /** 兜底：未知异常按系统繁忙返回，不泄露堆栈与内部细节。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("[OpenAPI] 系统异常", e);
        return fail(OpenApiErrorCode.SYSTEM_ERROR, OpenApiErrorCode.SYSTEM_ERROR.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> fail(OpenApiErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.fail(errorCode, message));
    }
}
