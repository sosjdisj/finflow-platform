package org.example.finflowbackend.openapi.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.common.idempotent.Idempotent;
import org.example.finflowbackend.openapi.api.OpenApiErrorCode;
import org.example.finflowbackend.openapi.security.OpenApiAuthFilter;
import org.example.finflowbackend.openapi.security.OpenApiCaller;
import org.example.finflowbackend.openapi.service.MerchantOpenApiService;
import org.example.finflowbackend.openapi.web.dto.OpenApiPaymentCreateRequest;
import org.example.finflowbackend.openapi.web.dto.OpenApiPaymentResponse;
import org.example.finflowbackend.openapi.web.dto.OpenApiRefundCreateRequest;
import org.example.finflowbackend.openapi.web.dto.OpenApiRefundResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商户 OpenAPI（M12）：/open/v1/** 由 {@link OpenApiAuthFilter} 完成 AK/SK 验签，
 * 控制器不接触凭证，只从请求属性取已验签的调用方，杜绝请求体伪造 merchantId。
 *
 * <p>写接口（下单 / 退款）标注 {@link Idempotent}，客户端须携带 Idempotency-Key，
 * 相同键重复提交直接回放首次响应；读接口无需幂等键。</p>
 */
@RestController
@Tag(name = "商户 OpenAPI")
@RequestMapping(OpenApiAuthFilter.BASE_PATH)
public class OpenApiController {

    private final MerchantOpenApiService openApiService;

    public OpenApiController(MerchantOpenApiService openApiService) {
        this.openApiService = openApiService;
    }

    @PostMapping("/payments")
    @Idempotent
    public ApiResponse<OpenApiPaymentResponse> createPayment(
            @Valid @RequestBody OpenApiPaymentCreateRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(openApiService.createPayment(caller(servletRequest).merchantId(), request));
    }

    @GetMapping("/payments/{platformOrderNo}")
    public ApiResponse<OpenApiPaymentResponse> queryPayment(@PathVariable String platformOrderNo,
                                                            HttpServletRequest servletRequest) {
        return ApiResponse.ok(openApiService.queryPayment(caller(servletRequest).merchantId(), platformOrderNo));
    }

    @PostMapping("/payments/{platformOrderNo}/close")
    @Idempotent
    public ApiResponse<OpenApiPaymentResponse> closePayment(@PathVariable String platformOrderNo,
                                                            HttpServletRequest servletRequest) {
        return ApiResponse.ok(openApiService.closePayment(caller(servletRequest).merchantId(), platformOrderNo));
    }

    @PostMapping("/refunds")
    @Idempotent
    public ApiResponse<OpenApiRefundResponse> createRefund(
            @Valid @RequestBody OpenApiRefundCreateRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(openApiService.createRefund(caller(servletRequest).merchantId(), request));
    }

    @GetMapping("/refunds/{refundNo}")
    public ApiResponse<OpenApiRefundResponse> queryRefund(@PathVariable String refundNo,
                                                          HttpServletRequest servletRequest) {
        return ApiResponse.ok(openApiService.queryRefund(caller(servletRequest).merchantId(), refundNo));
    }

    /** 调用方由验签过滤器写入；缺失说明路径未经验签链路，按鉴权失败拒绝（兜底）。 */
    private OpenApiCaller caller(HttpServletRequest request) {
        Object caller = request.getAttribute(OpenApiCaller.ATTRIBUTE);
        if (caller instanceof OpenApiCaller openApiCaller) {
            return openApiCaller;
        }
        throw new BusinessException(OpenApiErrorCode.AUTH_FAILED);
    }
}
