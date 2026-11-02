package org.example.finflowbackend.openapi.service;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.openapi.api.OpenApiErrorCode;
import org.example.finflowbackend.openapi.quota.OpenApiApiCode;
import org.example.finflowbackend.openapi.quota.OpenApiQuotaService;
import org.example.finflowbackend.openapi.web.dto.OpenApiPaymentCreateRequest;
import org.example.finflowbackend.openapi.web.dto.OpenApiPaymentResponse;
import org.example.finflowbackend.openapi.web.dto.OpenApiRefundCreateRequest;
import org.example.finflowbackend.openapi.web.dto.OpenApiRefundResponse;
import org.example.finflowbackend.payment.api.PaymentOrderApi;
import org.example.finflowbackend.payment.api.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.api.PaymentOrderResponse;
import org.example.finflowbackend.payment.api.PaymentProcessApi;
import org.example.finflowbackend.refund.api.RefundApi;
import org.example.finflowbackend.refund.api.RefundOrderSnapshot;
import org.springframework.stereotype.Service;

/**
 * 商户 OpenAPI 业务编排（M12）：只做「配额校验 + 归属校验 + 转发内部能力」，
 * 不复制支付 / 退款逻辑，费率、风控、幂等等全部沿用后台主链路。
 *
 * <p>商户身份一律取自验签凭证（{@code merchantId} 由调用方从 {@code OpenApiCaller} 传入），
 * 请求体里的单号只用于定位资源；访问他商户资源统一按「不存在」返回，避免订单探测。</p>
 */
@Service
public class MerchantOpenApiService {

    private final OpenApiQuotaService quotaService;
    private final PaymentProcessApi paymentProcessApi;
    private final PaymentOrderApi paymentOrderApi;
    private final RefundApi refundApi;

    public MerchantOpenApiService(OpenApiQuotaService quotaService, PaymentProcessApi paymentProcessApi,
                                  PaymentOrderApi paymentOrderApi, RefundApi refundApi) {
        this.quotaService = quotaService;
        this.paymentProcessApi = paymentProcessApi;
        this.paymentOrderApi = paymentOrderApi;
        this.refundApi = refundApi;
    }

    /** 沙箱下单：与后台建单同一契约、同一编排（幂等键为 merchantOrderNo）。 */
    public OpenApiPaymentResponse createPayment(Long merchantId, OpenApiPaymentCreateRequest request) {
        quotaService.check(merchantId, OpenApiApiCode.PAYMENT_CREATE);
        PaymentOrderResponse order = paymentProcessApi.createAndProcess(new PaymentOrderCreateRequest(
                merchantId, request.merchantOrderNo(), request.amount(), request.currency(), request.subject(),
                request.body(), request.paymentMethod(), request.callbackUrl(), request.expiredAt(),
                request.clientIp(), request.deviceId()));
        return OpenApiPaymentResponse.from(order);
    }

    public OpenApiPaymentResponse queryPayment(Long merchantId, String platformOrderNo) {
        quotaService.check(merchantId, OpenApiApiCode.PAYMENT_QUERY);
        return OpenApiPaymentResponse.from(requireOwnedOrder(merchantId, platformOrderNo));
    }

    /** 商户关单：状态机只允许 CREATED / PENDING → CLOSED，返回关单后的订单视图。 */
    public OpenApiPaymentResponse closePayment(Long merchantId, String platformOrderNo) {
        quotaService.check(merchantId, OpenApiApiCode.PAYMENT_CLOSE);
        paymentOrderApi.closeByMerchant(platformOrderNo, merchantId, operator(merchantId));
        return OpenApiPaymentResponse.from(requireOwnedOrder(merchantId, platformOrderNo));
    }

    public OpenApiRefundResponse createRefund(Long merchantId, OpenApiRefundCreateRequest request) {
        quotaService.check(merchantId, OpenApiApiCode.REFUND_CREATE);
        PaymentOrderResponse order = requireOwnedOrder(merchantId, request.platformOrderNo());
        RefundOrderSnapshot refund = refundApi.createRefund(order.id(), request.amount(), request.reason(),
                operator(merchantId));
        return OpenApiRefundResponse.from(refund);
    }

    public OpenApiRefundResponse queryRefund(Long merchantId, String refundNo) {
        quotaService.check(merchantId, OpenApiApiCode.REFUND_QUERY);
        RefundOrderSnapshot refund = refundApi.findByRefundNo(refundNo);
        if (!merchantId.equals(refund.merchantId())) {
            throw new BusinessException(OpenApiErrorCode.NOT_FOUND);
        }
        return OpenApiRefundResponse.from(refund);
    }

    /** 读订单并校验归属；他商户订单与不存在同为 NOT_FOUND。 */
    private PaymentOrderResponse requireOwnedOrder(Long merchantId, String platformOrderNo) {
        PaymentOrderResponse order = paymentOrderApi.findByPlatformOrderNo(platformOrderNo);
        if (!merchantId.equals(order.merchantId())) {
            throw new BusinessException(OpenApiErrorCode.NOT_FOUND);
        }
        return order;
    }

    /** 留痕操作人：区分 OpenAPI 与后台人工操作。 */
    private static String operator(Long merchantId) {
        return "OPENAPI:" + merchantId;
    }
}
