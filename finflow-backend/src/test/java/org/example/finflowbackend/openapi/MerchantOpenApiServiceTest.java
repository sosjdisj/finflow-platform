package org.example.finflowbackend.openapi;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.openapi.api.OpenApiErrorCode;
import org.example.finflowbackend.openapi.quota.OpenApiApiCode;
import org.example.finflowbackend.openapi.quota.OpenApiQuotaService;
import org.example.finflowbackend.openapi.service.MerchantOpenApiService;
import org.example.finflowbackend.openapi.web.dto.OpenApiPaymentCreateRequest;
import org.example.finflowbackend.openapi.web.dto.OpenApiPaymentResponse;
import org.example.finflowbackend.openapi.web.dto.OpenApiRefundCreateRequest;
import org.example.finflowbackend.payment.api.PaymentOrderApi;
import org.example.finflowbackend.payment.api.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.api.PaymentOrderResponse;
import org.example.finflowbackend.payment.api.PaymentProcessApi;
import org.example.finflowbackend.refund.api.RefundApi;
import org.example.finflowbackend.refund.api.RefundOrderSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * M12 OpenAPI 编排单测：商户身份只来源于凭证、越权一律 NOT_FOUND、写操作先过配额再落业务。
 */
class MerchantOpenApiServiceTest {

    private final OpenApiQuotaService quotaService = mock(OpenApiQuotaService.class);
    private final PaymentProcessApi paymentProcessApi = mock(PaymentProcessApi.class);
    private final PaymentOrderApi paymentOrderApi = mock(PaymentOrderApi.class);
    private final RefundApi refundApi = mock(RefundApi.class);
    private final MerchantOpenApiService service =
            new MerchantOpenApiService(quotaService, paymentProcessApi, paymentOrderApi, refundApi);

    @Test
    void createPaymentUsesCredentialMerchantIdAndForwardsContract() {
        when(paymentProcessApi.createAndProcess(any())).thenReturn(order(1L, "P1", "CREATED"));

        OpenApiPaymentResponse response = service.createPayment(1L, new OpenApiPaymentCreateRequest(
                "M-1", 10000L, "CNY", "沙箱下单", null, "ALIPAY", null, null, "1.1.1.1", "dev-1"));

        verify(quotaService).check(1L, OpenApiApiCode.PAYMENT_CREATE);
        ArgumentCaptor<PaymentOrderCreateRequest> captor = ArgumentCaptor.forClass(PaymentOrderCreateRequest.class);
        verify(paymentProcessApi).createAndProcess(captor.capture());
        assertEquals(1L, captor.getValue().merchantId());
        assertEquals("M-1", captor.getValue().merchantOrderNo());
        assertEquals(10000L, response.amount());
    }

    @Test
    void queryPaymentOfOtherMerchantIsNotFound() {
        when(paymentOrderApi.findByPlatformOrderNo("P1")).thenReturn(order(2L, "P1", "PAID"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.queryPayment(1L, "P1"));

        assertEquals(OpenApiErrorCode.NOT_FOUND, error.getErrorCode());
        verify(quotaService).check(1L, OpenApiApiCode.PAYMENT_QUERY);
    }

    @Test
    void closePaymentDelegatesWithOwnershipGuardAndReturnsClosedState() {
        when(paymentOrderApi.findByPlatformOrderNo("P1")).thenReturn(order(1L, "P1", "CLOSED"));

        OpenApiPaymentResponse response = service.closePayment(1L, "P1");

        verify(paymentOrderApi).closeByMerchant("P1", 1L, "OPENAPI:1");
        assertEquals("CLOSED", response.status());
    }

    @Test
    void createRefundRequiresOrderOwnership() {
        when(paymentOrderApi.findByPlatformOrderNo("P1")).thenReturn(order(2L, "P1", "PAID"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createRefund(1L, new OpenApiRefundCreateRequest("P1", 100L, "测试")));

        assertEquals(OpenApiErrorCode.NOT_FOUND, error.getErrorCode());
        verifyNoInteractions(refundApi);
    }

    @Test
    void createRefundPassesOwnOrderIdAndOperator() {
        when(paymentOrderApi.findByPlatformOrderNo("P1")).thenReturn(order(1L, "P1", "PAID"));
        when(refundApi.createRefund(11L, 100L, "测试", "OPENAPI:1")).thenReturn(refund(1L, "R1", "APPLYING"));

        var response = service.createRefund(1L, new OpenApiRefundCreateRequest("P1", 100L, "测试"));

        verify(quotaService).check(1L, OpenApiApiCode.REFUND_CREATE);
        assertEquals("R1", response.refundNo());
        assertEquals(100L, response.refundAmount());
    }

    @Test
    void queryRefundOfOtherMerchantIsNotFound() {
        when(refundApi.findByRefundNo("R1")).thenReturn(refund(2L, "R1", "SUCCESS"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.queryRefund(1L, "R1"));

        assertEquals(OpenApiErrorCode.NOT_FOUND, error.getErrorCode());
    }

    private PaymentOrderResponse order(Long merchantId, String platformOrderNo, String status) {
        return new PaymentOrderResponse(11L, merchantId, "M-1", platformOrderNo, 10000L, 0L, "CNY",
                "沙箱下单", null, "ALIPAY", null, null, status, LocalDateTime.now());
    }

    private RefundOrderSnapshot refund(Long merchantId, String refundNo, String status) {
        return new RefundOrderSnapshot(1L, refundNo, 11L, merchantId, "P1", 100L, "CNY", "测试",
                null, null, status, null, null, LocalDateTime.now());
    }
}
