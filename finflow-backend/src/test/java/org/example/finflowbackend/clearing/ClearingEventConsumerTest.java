package org.example.finflowbackend.clearing;

import org.example.finflowbackend.clearing.entity.ClearingDetail;
import org.example.finflowbackend.clearing.entity.ClearingOrder;
import org.example.finflowbackend.clearing.event.ClearingEventConsumer;
import org.example.finflowbackend.clearing.mapper.ClearingDetailMapper;
import org.example.finflowbackend.clearing.mapper.ClearingOrderMapper;
import org.example.finflowbackend.clearing.service.FeeCalculator;
import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.common.event.EventDeduplicationService;
import org.example.finflowbackend.merchant.dto.ClearingFeeRateView;
import org.example.finflowbackend.merchant.service.RateService;
import org.example.finflowbackend.payment.dto.PaymentOrderResponse;
import org.example.finflowbackend.payment.event.PaymentSucceededPayload;
import org.example.finflowbackend.payment.event.PaymentTopics;
import org.example.finflowbackend.payment.service.ChannelConfigService;
import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.example.finflowbackend.payment.service.PaymentTransactionService;
import org.example.finflowbackend.system.config.ConfigKeys;
import org.example.finflowbackend.system.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 清分消费者单测：正常落 1 单 3 行、去重跳过、uk 兜底跳过、渠道缺失兜底。
 */
class ClearingEventConsumerTest {

    private static final Long ORDER_ID = 13L;
    private static final Long MERCHANT_ID = 10L;
    private static final String EVENT_ID = "evt-0001";

    private EventDeduplicationService deduplicationService;
    private ClearingOrderMapper clearingOrderMapper;
    private ClearingDetailMapper clearingDetailMapper;
    private PaymentOrderService paymentOrderService;
    private PaymentTransactionService transactionService;
    private ChannelConfigService channelConfigService;
    private RateService rateService;
    private ConfigService configService;
    private ClearingEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        deduplicationService = mock(EventDeduplicationService.class);
        clearingOrderMapper = mock(ClearingOrderMapper.class);
        clearingDetailMapper = mock(ClearingDetailMapper.class);
        paymentOrderService = mock(PaymentOrderService.class);
        transactionService = mock(PaymentTransactionService.class);
        channelConfigService = mock(ChannelConfigService.class);
        rateService = mock(RateService.class);
        configService = mock(ConfigService.class);
        when(configService.getLong(ConfigKeys.MERCHANT_RATE_DEFAULT_BPS)).thenReturn(60L);
        objectMapper = new ObjectMapper();
        consumer = new ClearingEventConsumer(deduplicationService, objectMapper,
                clearingOrderMapper, clearingDetailMapper, new FeeCalculator(),
                paymentOrderService, transactionService, channelConfigService, rateService, configService);
    }

    @Test
    void consumesSucceededEventAndWritesOrderWithThreeDetails() {
        stubHappyPath(10000L, 100L);
        when(clearingOrderMapper.selectCount(any())).thenReturn(0L);

        consumer.handle(message());

        ArgumentCaptor<ClearingOrder> orderCaptor = ArgumentCaptor.forClass(ClearingOrder.class);
        verify(clearingOrderMapper).insert(orderCaptor.capture());
        ClearingOrder clearing = orderCaptor.getValue();
        assertEquals(ORDER_ID, clearing.getOrderId());
        assertEquals(MERCHANT_ID, clearing.getMerchantId());
        assertEquals(10000L, clearing.getPayAmount());
        assertEquals(9700L, clearing.getMerchantIncome());
        assertEquals(200L, clearing.getPlatformFee());
        assertEquals(100L, clearing.getChannelCost());
        assertEquals("MOCK_WALLET", clearing.getChannelCode());
        assertEquals("CLEARED", clearing.getStatus());
        assertEquals(LocalDateTime.of(2026, 10, 17, 12, 0).toLocalDate(), clearing.getSettleDate());

        ArgumentCaptor<ClearingDetail> detailCaptor = ArgumentCaptor.forClass(ClearingDetail.class);
        verify(clearingDetailMapper, org.mockito.Mockito.times(3)).insert(detailCaptor.capture());
        List<ClearingDetail> details = detailCaptor.getAllValues();
        assertEquals(9700L, amount(details, "MERCHANT"));
        assertEquals(200L, amount(details, "PLATFORM"));
        assertEquals(100L, amount(details, "CHANNEL"));
        assertEquals(Long.valueOf(MERCHANT_ID), details.stream()
                .filter(d -> "MERCHANT".equals(d.getPartyType())).findFirst().orElseThrow().getPartyId());
        assertEquals(Long.valueOf(0L), details.stream()
                .filter(d -> "PLATFORM".equals(d.getPartyType())).findFirst().orElseThrow().getPartyId());
    }

    @Test
    void duplicateEventIsSkipped() {
        when(deduplicationService.tryConsume(anyString(), eq(EVENT_ID))).thenReturn(false);

        consumer.handle(message());

        verify(paymentOrderService, never()).get(any());
        verify(clearingOrderMapper, never()).insert(any(ClearingOrder.class));
        verify(clearingDetailMapper, never()).insert(any(ClearingDetail.class));
    }

    @Test
    void alreadyClearedOrderIsSkipped() {
        stubHappyPath(10000L, 100L);
        when(clearingOrderMapper.selectCount(any())).thenReturn(1L);

        consumer.handle(message());

        verify(clearingOrderMapper, never()).insert(any(ClearingOrder.class));
        verify(clearingDetailMapper, never()).insert(any(ClearingDetail.class));
    }

    @Test
    void duplicateKeyOnInsertFallsBackToIdempotentSkip() {
        stubHappyPath(10000L, 100L);
        when(clearingOrderMapper.selectCount(any())).thenReturn(0L);
        when(clearingOrderMapper.insert(any(ClearingOrder.class)))
                .thenThrow(new DuplicateKeyException("uk_clearing_order_order"));

        consumer.handle(message());

        verify(clearingDetailMapper, never()).insert(any(ClearingDetail.class));
    }

    @Test
    void missingChannelInteractionFallsBackToUnknownWithZeroCost() {
        when(deduplicationService.tryConsume(anyString(), anyString())).thenReturn(true);
        when(paymentOrderService.get(ORDER_ID)).thenReturn(order(10000L));
        when(transactionService.findChannelCode(ORDER_ID)).thenReturn(null);
        when(rateService.getFeeRateView(MERCHANT_ID))
                .thenReturn(new ClearingFeeRateView(MERCHANT_ID, ClearingFeeRateView.MODE_PLAIN, 200L, null, List.of()));
        when(clearingOrderMapper.selectCount(any())).thenReturn(0L);

        consumer.handle(message());

        ArgumentCaptor<ClearingOrder> captor = ArgumentCaptor.forClass(ClearingOrder.class);
        verify(clearingOrderMapper).insert(captor.capture());
        assertEquals("UNKNOWN", captor.getValue().getChannelCode());
        assertEquals(0L, captor.getValue().getChannelCost());
        assertEquals(9800L, captor.getValue().getMerchantIncome());
        verify(channelConfigService, never()).getCostRateBps(anyString());
    }

    /** S37：商户未配置费率时按平台默认费率（万分比 60=0.6%）兜底计手续费。 */
    @Test
    void missingMerchantRateFallsBackToDefaultBps() {
        when(deduplicationService.tryConsume(anyString(), anyString())).thenReturn(true);
        when(paymentOrderService.get(ORDER_ID)).thenReturn(order(10000L));
        when(transactionService.findChannelCode(ORDER_ID)).thenReturn(null);
        when(rateService.getFeeRateView(MERCHANT_ID)).thenReturn(null);
        when(clearingOrderMapper.selectCount(any())).thenReturn(0L);

        consumer.handle(message());

        ArgumentCaptor<ClearingOrder> captor = ArgumentCaptor.forClass(ClearingOrder.class);
        verify(clearingOrderMapper).insert(captor.capture());
        // 10000 × 0.6% = 60 平台手续费，渠道成本 0，商户得 9940
        assertEquals(60L, captor.getValue().getPlatformFee());
        assertEquals(9940L, captor.getValue().getMerchantIncome());
        assertEquals(0L, captor.getValue().getChannelCost());
    }

    private void stubHappyPath(long amount, long channelCostBps) {
        when(deduplicationService.tryConsume(anyString(), eq(EVENT_ID))).thenReturn(true);
        when(paymentOrderService.get(ORDER_ID)).thenReturn(order(amount));
        when(transactionService.findChannelCode(ORDER_ID)).thenReturn("MOCK_WALLET");
        when(rateService.getFeeRateView(MERCHANT_ID))
                .thenReturn(new ClearingFeeRateView(MERCHANT_ID, ClearingFeeRateView.MODE_PLAIN, 200L, null, List.of()));
        when(channelConfigService.getCostRateBps("MOCK_WALLET")).thenReturn(channelCostBps);
    }

    private PaymentOrderResponse order(long amount) {
        return new PaymentOrderResponse(ORDER_ID, MERCHANT_ID, "MO-1", "P20261017000013",
                amount, 0L, "CNY", "清分验收商品", null, "ALIPAY", null, null,
                "PAID", LocalDateTime.of(2026, 10, 17, 11, 59));
    }

    private String message() {
        DomainEvent event = new DomainEvent(EVENT_ID, PaymentTopics.TYPE_SUCCEEDED, Instant.now(),
                new PaymentSucceededPayload(ORDER_ID, "P20261017000013", 10000L, "CNY",
                        LocalDateTime.of(2026, 10, 17, 12, 0)));
        return objectMapper.writeValueAsString(event);
    }

    private long amount(List<ClearingDetail> details, String partyType) {
        return details.stream().filter(d -> partyType.equals(d.getPartyType()))
                .findFirst().orElseThrow().getAmount();
    }
}
