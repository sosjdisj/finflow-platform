package org.example.finflowbackend.payment.service;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.payment.PaymentErrorCode;
import org.example.finflowbackend.payment.PaymentStatus;
import org.example.finflowbackend.payment.channel.ChannelRequest;
import org.example.finflowbackend.payment.channel.ChannelResult;
import org.example.finflowbackend.payment.channel.PaymentChannel;
import org.example.finflowbackend.payment.channel.PaymentChannelRouter;
import org.example.finflowbackend.payment.dto.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.dto.PaymentOrderDetailResponse;
import org.example.finflowbackend.payment.dto.PaymentOrderResponse;
import org.example.finflowbackend.payment.entity.PaymentOrder;
import org.example.finflowbackend.payment.risk.RiskCheckContext;
import org.example.finflowbackend.payment.risk.RiskChecker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付编排单测：受理链路、风控拒绝、渠道失败终态化、幂等回放不重走受理。
 */
class PaymentProcessServiceTest {

    private final PaymentOrderService paymentOrderService = mock(PaymentOrderService.class);
    private final RiskChecker riskChecker = mock(RiskChecker.class);
    private final PaymentChannelRouter channelRouter = mock(PaymentChannelRouter.class);
    private final PaymentChannel channel = mock(PaymentChannel.class);

    private final PaymentProcessService processService =
            new PaymentProcessService(paymentOrderService, riskChecker, channelRouter);

    @Test
    void createAndProcessRoutesToChannelAndReturnsLatestOrder() {
        stubCreatedOrder();
        when(paymentOrderService.getDetail(7L)).thenReturn(detail("PENDING"));
        when(riskChecker.check(any())).thenReturn(RiskCheckContext.Decision.allow());
        when(channelRouter.route("ALIPAY")).thenReturn(channel);
        when(channel.createPayment(any())).thenReturn(new ChannelResult(true, "MW20261017111111", "PENDING", "受理中"));

        PaymentOrderCreateRequest request = createRequest();
        PaymentOrderResponse result = processService.createAndProcess(request);

        assertEquals(7L, result.id());
        assertEquals("PENDING", result.status());
        verify(paymentOrderService).changeStatus(7L, "PENDING", "发起支付，等待渠道受理", "PROCESS");
        ArgumentCaptor<ChannelRequest> captor = ArgumentCaptor.forClass(ChannelRequest.class);
        verify(channel).createPayment(captor.capture());
        assertEquals(10000L, captor.getValue().amount());
        assertEquals("ALIPAY", captor.getValue().paymentMethod());
    }

    @Test
    void riskRejectionFinalizesOrderAndThrows() {
        stubCreatedOrder();
        when(riskChecker.check(any())).thenReturn(RiskCheckContext.Decision.reject("黑名单商户"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> processService.createAndProcess(createRequest()));

        assertEquals(PaymentErrorCode.RISK_REJECTED, error.getErrorCode());
        verify(paymentOrderService).changeStatus(7L, "PENDING", "发起支付，等待渠道受理", "PROCESS");
        // BusinessException 携带的是构造时传入的自定义原因
        verify(paymentOrderService).changeStatus(7L, "FAILED", "受理失败: 黑名单商户", "PROCESS");
        verify(channelRouter, never()).route(any());
    }

    @Test
    void riskReviewSuspendsOrderWithoutChannelRouting() {
        stubCreatedOrder();
        when(riskChecker.check(any()))
                .thenReturn(RiskCheckContext.Decision.review("风险分 30 达到审核阈值 30"));

        PaymentOrderResponse result = processService.createAndProcess(createRequest());

        // REVIEW：订单停在待审，正常返回不抛异常，不路由渠道
        verify(paymentOrderService).changeStatus(7L, "PENDING", "发起支付，等待渠道受理", "PROCESS");
        verify(paymentOrderService).changeStatus(eq(7L), eq("REVIEW"),
                eq("风控待人工审核: 风险分 30 达到审核阈值 30"), eq("RISK"));
        verify(channelRouter, never()).route(any());
        assertEquals(7L, result.id());
    }

    @Test
    void reviewApproveResumesChannelAcceptance() {
        when(paymentOrderService.getDetail(7L)).thenReturn(detail("PENDING"));
        when(paymentOrderService.get(7L)).thenReturn(response("PENDING"));
        when(channelRouter.route("ALIPAY")).thenReturn(channel);
        when(channel.createPayment(any()))
                .thenReturn(new ChannelResult(true, "MW20261017123456", "PENDING", "受理中"));

        processService.resumeAfterReview(7L, true, "admin", "大额订单人工放行");

        // 通过后回 PENDING 并直接渠道路由（人工放行即风控结论，不再重查风控）
        verify(paymentOrderService).changeStatus(7L, "PENDING", "风控人工审核通过: 大额订单人工放行", "admin");
        verify(riskChecker, never()).check(any());
        verify(channel).createPayment(any());
    }

    @Test
    void reviewRejectFinalizesOrder() {
        processService.resumeAfterReview(7L, false, "admin", "确认欺诈订单");

        verify(paymentOrderService).changeStatus(7L, "FAILED", "风控人工审核拒绝: 确认欺诈订单", "admin");
        verify(channelRouter, never()).route(any());
    }

    @Test
    void reviewApproveChannelFailureFinalizesOrder() {
        when(paymentOrderService.getDetail(7L)).thenReturn(detail("PENDING"));
        when(channelRouter.route("ALIPAY")).thenThrow(new BusinessException(PaymentErrorCode.CHANNEL_NOT_FOUND));

        assertThrows(BusinessException.class, () -> processService.resumeAfterReview(7L, true, "admin", null));

        verify(paymentOrderService).changeStatus(eq(7L), eq("FAILED"), any(), eq("PROCESS"));
    }

    @Test
    void channelRoutingFailureFinalizesOrderAndRethrows() {
        stubCreatedOrder();
        when(riskChecker.check(any())).thenReturn(RiskCheckContext.Decision.allow());
        when(channelRouter.route("ALIPAY")).thenThrow(new BusinessException(PaymentErrorCode.CHANNEL_NOT_FOUND));

        assertThrows(BusinessException.class, () -> processService.createAndProcess(createRequest()));

        verify(paymentOrderService).changeStatus(7L, "FAILED", "受理失败: 该支付方式无可用支付渠道", "PROCESS");
    }

    @Test
    void channelRejectionFinalizesOrderAndRethrows() {
        stubCreatedOrder();
        when(riskChecker.check(any())).thenReturn(RiskCheckContext.Decision.allow());
        when(channelRouter.route("ALIPAY")).thenReturn(channel);
        when(channel.createPayment(any())).thenReturn(new ChannelResult(false, null, null, "渠道停用"));

        assertThrows(BusinessException.class, () -> processService.createAndProcess(createRequest()));

        verify(paymentOrderService).changeStatus(eq(7L), eq("FAILED"), any(), eq("PROCESS"));
    }

    @Test
    void replayedOrderSkipsProcessing() {
        when(paymentOrderService.create(any())).thenReturn(response("PAID"));

        PaymentOrderResponse result = processService.createAndProcess(createRequest());

        assertEquals("PAID", result.status());
        verify(paymentOrderService, never()).changeStatus(any(), any(), any(), any());
        verify(riskChecker, never()).check(any());
        verify(channelRouter, never()).route(any());
    }

    private void stubCreatedOrder() {
        when(paymentOrderService.create(any())).thenReturn(response("CREATED"));
        when(paymentOrderService.getDetail(7L)).thenReturn(detail("PENDING"));
        // 渠道受理需平台单号/商户单号，订单读取返回同款响应
        when(paymentOrderService.get(7L)).thenReturn(response("PENDING"));
    }

    private PaymentOrderResponse response(String status) {
        PaymentOrder order = new PaymentOrder();
        order.setId(7L);
        order.setMerchantId(1L);
        order.setPlatformOrderNo("P20261017000001");
        order.setMerchantOrderNo("M20261017000001");
        order.setAmount(10000L);
        order.setPaymentMethod("ALIPAY");
        order.setSubject("测试商品");
        order.setStatus(status);
        return PaymentOrderResponse.from(order);
    }

    private PaymentOrderDetailResponse detail(String status) {
        return new PaymentOrderDetailResponse(response(status), java.util.List.of(), java.util.List.of());
    }

    private PaymentOrderCreateRequest createRequest() {
        return new PaymentOrderCreateRequest(1L, "ORD-1", 10000L, null, "测试商品", null, "ALIPAY",
                null, null, null, null);
    }
}
