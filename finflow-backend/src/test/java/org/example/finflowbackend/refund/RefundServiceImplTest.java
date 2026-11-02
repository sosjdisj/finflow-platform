package org.example.finflowbackend.refund;

import org.example.finflowbackend.channel.api.ChannelErrorCode;
import org.example.finflowbackend.channel.api.ChannelOperationsApi;
import org.example.finflowbackend.channel.api.ChannelResult;
import org.example.finflowbackend.channel.api.PaymentTransactionApi;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.payment.api.PaymentOrderResponse;
import org.example.finflowbackend.payment.api.PaymentOrderSnapshot;
import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.example.finflowbackend.refund.api.RefundErrorCode;
import org.example.finflowbackend.refund.dto.RefundCreateRequest;
import org.example.finflowbackend.refund.dto.RefundOrderResponse;
import org.example.finflowbackend.refund.entity.RefundOrder;
import org.example.finflowbackend.refund.mapper.RefundOrderMapper;
import org.example.finflowbackend.refund.mapper.RefundOrderStateLogMapper;
import org.example.finflowbackend.refund.service.RefundServiceImpl;
import org.example.finflowbackend.refund.service.RefundStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款编排单测：创建（成功/渠道拒绝/路由失败/落单补偿）与渠道回调（成功/失败/幂等忽略）。
 * 状态推进均委托 RefundStatusService，这里验证委托关系而非推进细节。
 */
class RefundServiceImplTest {

    private static final Long ORDER_ID = 1L;
    private static final Long REFUND_ID = 99L;
    private static final String REFUND_NO = "R20260101000001";

    private RefundOrderMapper refundMapper;
    private PaymentOrderService paymentOrderService;
    private PaymentTransactionApi transactionService;
    private ChannelOperationsApi channelOperations;
    private RefundStatusService refundStatusService;
    private RefundServiceImpl refundService;

    @BeforeEach
    void setUp() {
        refundMapper = mock(RefundOrderMapper.class);
        paymentOrderService = mock(PaymentOrderService.class);
        transactionService = mock(PaymentTransactionApi.class);
        channelOperations = mock(ChannelOperationsApi.class);
        refundStatusService = mock(RefundStatusService.class);
        refundService = new RefundServiceImpl(refundMapper, mock(RefundOrderStateLogMapper.class),
                paymentOrderService, transactionService, channelOperations, refundStatusService);
        // insert 替身回填主键，模拟自增
        doAnswer(inv -> {
            RefundOrder refund = inv.getArgument(0);
            refund.setId(REFUND_ID);
            return 1;
        }).when(refundMapper).insert(any(RefundOrder.class));
    }

    @Test
    void createAcceptsAndMovesToProcessing() {
        when(paymentOrderService.reserveRefundQuota(ORDER_ID, 3000L, "admin")).thenReturn(paidOrder());
        when(channelOperations.refund(any()))
                .thenReturn(new ChannelResult("MOCK_WALLET", true, "CR1", "PROCESSING", "退款受理中"));
        RefundOrder processing = applyingRefund();
        processing.setStatus("PROCESSING");
        when(refundMapper.selectById(REFUND_ID)).thenReturn(processing);

        RefundOrderResponse response = refundService.create(
                new RefundCreateRequest(ORDER_ID, 3000L, "七天无理由"), "admin");

        assertEquals("PROCESSING", response.status());
        ArgumentCaptor<RefundOrder> captor = ArgumentCaptor.forClass(RefundOrder.class);
        verify(refundMapper).insert(captor.capture());
        RefundOrder inserted = captor.getValue();
        assertEquals("APPLYING", inserted.getStatus());
        assertEquals(3000L, inserted.getRefundAmount());
        assertEquals(ORDER_ID, inserted.getOrderId());
        assertTrue(inserted.getRefundNo().startsWith("R"));
        verify(refundStatusService).markAccepted(REFUND_ID, "MOCK_WALLET", "CR1");
    }

    @Test
    void createChannelRejectedFailsRefundAndReleasesQuota() {
        when(paymentOrderService.reserveRefundQuota(ORDER_ID, 3000L, "admin")).thenReturn(paidOrder());
        when(channelOperations.refund(any()))
                .thenReturn(new ChannelResult("MOCK_WALLET", false, null, null, "余额不足"));
        when(refundMapper.selectById(REFUND_ID)).thenReturn(applyingRefund());
        when(refundMapper.selectCount(any())).thenReturn(0L);
        when(paymentOrderService.get(ORDER_ID)).thenReturn(orderResponse(10000L, 0L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> refundService.create(new RefundCreateRequest(ORDER_ID, 3000L, null), "admin"));

        assertEquals(RefundErrorCode.CHANNEL_REFUND_REJECTED, error.getErrorCode());
        verify(refundStatusService).transition(eq(REFUND_ID), eq(RefundStatus.FAILED),
                contains("受理失败"), eq("admin"));
        verify(paymentOrderService).releaseRefundQuota(ORDER_ID, 3000L);
        // 无在途退款时支付订单从 REFUNDING 回退 PAID
        verify(paymentOrderService).changeStatus(eq(ORDER_ID), eq("PAID"), contains("退款失败释放额度"), any());
    }

    @Test
    void createRouteFailureFailsRefundAndReleasesQuota() {
        when(paymentOrderService.reserveRefundQuota(ORDER_ID, 3000L, "admin")).thenReturn(paidOrder());
        when(channelOperations.refund(any()))
                .thenThrow(new BusinessException(ChannelErrorCode.CHANNEL_NOT_FOUND));
        when(refundMapper.selectById(REFUND_ID)).thenReturn(applyingRefund());
        // 首次为退款单号查重（0=可用），随后为在途退款计数（1=仍有在途）
        when(refundMapper.selectCount(any())).thenReturn(0L, 1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> refundService.create(new RefundCreateRequest(ORDER_ID, 3000L, null), "admin"));

        assertEquals(ChannelErrorCode.CHANNEL_NOT_FOUND, error.getErrorCode());
        verify(refundStatusService).transition(eq(REFUND_ID), eq(RefundStatus.FAILED), any(), any());
        verify(paymentOrderService).releaseRefundQuota(ORDER_ID, 3000L);
        // 仍有其他在途退款时保持 REFUNDING
        verify(paymentOrderService, never()).changeStatus(any(), any(), any(), any());
    }

    @Test
    void createInsertFailureCompensatesQuota() {
        when(paymentOrderService.reserveRefundQuota(ORDER_ID, 3000L, "admin")).thenReturn(paidOrder());
        doThrow(new RuntimeException("db down")).when(refundMapper).insert(any(RefundOrder.class));

        assertThrows(RuntimeException.class,
                () -> refundService.create(new RefundCreateRequest(ORDER_ID, 3000L, null), "admin"));

        verify(paymentOrderService).releaseRefundQuota(ORDER_ID, 3000L);
        verify(refundStatusService, never()).transition(any(), any(), any(), any());
    }

    @Test
    void onResultSuccessFinalizesAndMarksFullyRefunded() {
        RefundOrder refund = processingRefund();
        when(refundMapper.selectOne(any())).thenReturn(refund);

        refundService.onResult(ORDER_ID, "MOCK_WALLET", REFUND_NO, "CR1", true, "退款成功");

        verify(refundStatusService).transition(eq(REFUND_ID), eq(RefundStatus.SUCCESS),
                contains("CR1"), eq("CHANNEL:MOCK_WALLET"));
        verify(refundStatusService, never()).markAccepted(any(), any(), any());
        verify(paymentOrderService).markFullyRefundedIfNeeded(ORDER_ID, REFUND_NO, "REFUND:" + REFUND_NO);
        verify(paymentOrderService, never()).releaseRefundQuota(anyLong(), anyLong());
    }

    @Test
    void onResultAtApplyingMarksAcceptedBeforeProcessingResult() {
        RefundOrder refund = applyingRefund();
        when(refundMapper.selectOne(any())).thenReturn(refund);

        refundService.onResult(ORDER_ID, "MOCK_WALLET", REFUND_NO, "CR1", true, "退款成功");

        verify(refundStatusService).markAccepted(REFUND_ID, "MOCK_WALLET", "CR1");
        verify(refundStatusService).transition(eq(REFUND_ID), eq(RefundStatus.SUCCESS),
                contains("CR1"), eq("CHANNEL:MOCK_WALLET"));
        verify(paymentOrderService).markFullyRefundedIfNeeded(ORDER_ID, REFUND_NO, "REFUND:" + REFUND_NO);
    }

    @Test
    void onResultFailureReleasesQuotaAndRevertsOrderWhenNoInflight() {
        RefundOrder refund = processingRefund();
        when(refundMapper.selectOne(any())).thenReturn(refund);
        when(refundMapper.selectCount(any())).thenReturn(0L);
        when(paymentOrderService.get(ORDER_ID))
                .thenReturn(orderResponse(10000L, 0L));

        refundService.onResult(ORDER_ID, "MOCK_WALLET", REFUND_NO, "CR1", false, "渠道退款失败");

        verify(refundStatusService).transition(eq(REFUND_ID), eq(RefundStatus.FAILED),
                eq("渠道退款失败"), eq("CHANNEL:MOCK_WALLET"));
        verify(paymentOrderService).releaseRefundQuota(ORDER_ID, 3000L);
        verify(paymentOrderService).changeStatus(eq(ORDER_ID), eq("PAID"), contains(REFUND_NO), any());
    }

    @Test
    void onResultFailureKeepsRefundingWhenInflightExists() {
        RefundOrder refund = processingRefund();
        when(refundMapper.selectOne(any())).thenReturn(refund);
        when(refundMapper.selectCount(any())).thenReturn(1L);

        refundService.onResult(ORDER_ID, "MOCK_WALLET", REFUND_NO, "CR1", false, "渠道退款失败");

        verify(refundStatusService).transition(eq(REFUND_ID), eq(RefundStatus.FAILED), any(), any());
        verify(paymentOrderService).releaseRefundQuota(ORDER_ID, 3000L);
        verify(paymentOrderService, never()).changeStatus(any(), any(), any(), any());
    }

    @Test
    void onResultUnknownRefundNoIsIgnored() {
        when(refundMapper.selectOne(any())).thenReturn(null);

        refundService.onResult(ORDER_ID, "MOCK_WALLET", "R_NOT_EXIST", "CR1", true, "退款成功");

        verify(refundStatusService, never()).transition(any(), any(), any(), any());
        verify(paymentOrderService, never()).markFullyRefundedIfNeeded(any(), any(), any());
    }

    @Test
    void onResultTerminalRefundIsIgnored() {
        RefundOrder refund = applyingRefund();
        refund.setStatus("SUCCESS");
        when(refundMapper.selectOne(any())).thenReturn(refund);

        refundService.onResult(ORDER_ID, "MOCK_WALLET", REFUND_NO, "CR1", true, "退款成功");

        verify(refundStatusService, never()).transition(any(), any(), any(), any());
    }

    private PaymentOrderSnapshot paidOrder() {
        return new PaymentOrderSnapshot(ORDER_ID, 10L, null, "P20260101000001", 10000L, 0L,
                "CNY", "退款单测商品", "ALIPAY", "PAID");
    }

    private RefundOrder applyingRefund() {
        RefundOrder refund = new RefundOrder();
        refund.setId(REFUND_ID);
        refund.setRefundNo(REFUND_NO);
        refund.setOrderId(ORDER_ID);
        refund.setMerchantId(10L);
        refund.setPlatformOrderNo("P20260101000001");
        refund.setRefundAmount(3000L);
        refund.setCurrency("CNY");
        refund.setStatus("APPLYING");
        refund.setOperator("admin");
        return refund;
    }

    private RefundOrder processingRefund() {
        RefundOrder refund = applyingRefund();
        refund.setStatus("PROCESSING");
        return refund;
    }

    private PaymentOrderResponse orderResponse(long amount, long refundedAmount) {
        return new PaymentOrderResponse(ORDER_ID, 10L, "MO-1", "P20260101000001", amount, refundedAmount,
                "CNY", "退款单测商品", null, "ALIPAY", null, null, "REFUNDING", null);
    }
}
