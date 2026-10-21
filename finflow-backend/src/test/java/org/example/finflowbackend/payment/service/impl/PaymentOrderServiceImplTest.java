package org.example.finflowbackend.payment.service.impl;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.merchant.MerchantErrorCode;
import org.example.finflowbackend.merchant.dto.MerchantDetailResponse;
import org.example.finflowbackend.merchant.dto.MerchantResponse;
import org.example.finflowbackend.merchant.service.MerchantService;
import org.example.finflowbackend.payment.PaymentErrorCode;
import org.example.finflowbackend.payment.dto.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.entity.PaymentOrder;
import org.example.finflowbackend.payment.entity.PaymentOrderStateLog;
import org.example.finflowbackend.payment.mapper.PaymentOrderMapper;
import org.example.finflowbackend.payment.mapper.PaymentOrderStateLogMapper;
import org.example.finflowbackend.system.config.ConfigKeys;
import org.example.finflowbackend.system.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付订单服务单测：建单初始化、商户状态校验、过期时间校验、详情含留痕。
 */
class PaymentOrderServiceImplTest {

    private final PaymentOrderMapper orderMapper = mock(PaymentOrderMapper.class);
    private final PaymentOrderStateLogMapper stateLogMapper = mock(PaymentOrderStateLogMapper.class);
    private final org.example.finflowbackend.payment.service.PaymentTransactionService transactionService =
            mock(org.example.finflowbackend.payment.service.PaymentTransactionService.class);
    private final MerchantService merchantService = mock(MerchantService.class);
    private final org.example.finflowbackend.payment.event.PaymentEventPublisher paymentEventPublisher =
            mock(org.example.finflowbackend.payment.event.PaymentEventPublisher.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final PaymentOrderServiceImpl service =
            new PaymentOrderServiceImpl(orderMapper, stateLogMapper, transactionService, merchantService,
                    paymentEventPublisher, configService);

    @BeforeEach
    void stubConfigDefaults() {
        // 配置中心默认值：开关开、限额 10 万、过期 2 小时（S37）
        when(configService.getBool(ConfigKeys.SWITCH_PAYMENT_CREATE)).thenReturn(true);
        when(configService.getLong(ConfigKeys.PAYMENT_SINGLE_LIMIT)).thenReturn(10_000_000L);
        when(configService.getLong(ConfigKeys.PAYMENT_ORDER_EXPIRE_HOURS)).thenReturn(2L);
    }

    @Test
    void createInitializesCreatedStatusAndPlatformOrderNo() {
        when(merchantService.getDetail(1L)).thenReturn(merchantDetail("ACTIVE"));
        when(orderMapper.selectCount(any())).thenReturn(0L);

        var response = service.create(request(1L, null));

        assertEquals("CREATED", response.status());
        assertTrue(response.platformOrderNo().startsWith("P"));
        assertEquals("CNY", response.currency());
        assertNotNull(response.expiredAt());

        ArgumentCaptor<PaymentOrder> captor = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(orderMapper).insert(captor.capture());
        assertEquals(10000L, captor.getValue().getAmount());
    }

    @Test
    void createKeepsGivenCurrencyAndExpiry() {
        when(merchantService.getDetail(1L)).thenReturn(merchantDetail("ACTIVE"));
        when(orderMapper.selectCount(any())).thenReturn(0L);
        LocalDateTime expiredAt = LocalDateTime.now().plusHours(6);

        var response = service.create(new PaymentOrderCreateRequest(1L, "MO-20261017001", 10000L,
                "USD", "测试商品", null, "ALIPAY", null, expiredAt, null, null));

        assertEquals("USD", response.currency());
        assertEquals(expiredAt, response.expiredAt());
    }

    @Test
    void createRejectsInactiveMerchant() {
        when(merchantService.getDetail(1L)).thenReturn(merchantDetail("SUSPENDED"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request(1L, null)));

        assertEquals(PaymentErrorCode.MERCHANT_NOT_ACTIVE, error.getErrorCode());
        verify(orderMapper, never()).insert(any(PaymentOrder.class));
    }

    /** S37：支付下单开关关闭时拒绝建单。 */
    @Test
    void createRejectsWhenPaymentSwitchOff() {
        when(configService.getBool(ConfigKeys.SWITCH_PAYMENT_CREATE)).thenReturn(false);

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request(1L, null)));

        assertEquals(PaymentErrorCode.SWITCH_OFF, error.getErrorCode());
        verify(merchantService, never()).getDetail(any());
    }

    /** S37：金额超过全局单笔限额时拒绝建单。 */
    @Test
    void createRejectsAmountOverSingleLimit() {
        when(configService.getLong(ConfigKeys.PAYMENT_SINGLE_LIMIT)).thenReturn(100L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(new PaymentOrderCreateRequest(1L, "MO-20261017001", 101L,
                        null, "测试商品", null, "ALIPAY", null, null, null, null)));

        assertEquals(PaymentErrorCode.AMOUNT_OVER_LIMIT, error.getErrorCode());
        verify(orderMapper, never()).insert(any(PaymentOrder.class));
    }

    /** S37：下单未传过期时间时按配置的小时数计算。 */
    @Test
    void createUsesConfiguredExpireHours() {
        when(merchantService.getDetail(1L)).thenReturn(merchantDetail("ACTIVE"));
        when(orderMapper.selectCount(any())).thenReturn(0L);

        var response = service.create(request(1L, null));

        assertNotNull(response.expiredAt());
        // 默认 2 小时配置 ± 容差
        assertTrue(response.expiredAt().isAfter(LocalDateTime.now().plusHours(1).plusMinutes(58)));
        assertTrue(response.expiredAt().isBefore(LocalDateTime.now().plusHours(2).plusMinutes(2)));
    }

    @Test
    void createPropagatesMerchantNotFound() {
        when(merchantService.getDetail(9L))
                .thenThrow(new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request(9L, null)));

        assertEquals(MerchantErrorCode.MERCHANT_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void createRejectsExpiredTimeInPast() {
        when(merchantService.getDetail(1L)).thenReturn(merchantDetail("ACTIVE"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(request(1L, LocalDateTime.now().minusMinutes(1))));

        assertEquals(PaymentErrorCode.EXPIRED_AT_INVALID, error.getErrorCode());
    }

    @Test
    void createReturnsExistingOrderOnDuplicateBusinessKey() {
        when(merchantService.getDetail(1L)).thenReturn(merchantDetail("ACTIVE"));
        when(orderMapper.insert(any(PaymentOrder.class)))
                .thenThrow(new DuplicateKeyException("uk_merchant_order"));
        when(orderMapper.selectOne(any())).thenReturn(order(7L));

        var response = service.create(request(1L, null));

        assertEquals(7L, response.id());
        assertEquals("P20261017000001", response.platformOrderNo());
    }

    @Test
    void createRethrowsNonBusinessKeyConflict() {
        when(merchantService.getDetail(1L)).thenReturn(merchantDetail("ACTIVE"));
        when(orderMapper.insert(any(PaymentOrder.class)))
                .thenThrow(new DuplicateKeyException("uk_platform_order_no"));
        when(orderMapper.selectOne(any())).thenReturn(null);

        assertThrows(DuplicateKeyException.class, () -> service.create(request(1L, null)));
    }

    @Test
    void changeStatusUpdatesOrderAndWritesStateLog() {
        when(orderMapper.selectById(5L)).thenReturn(order(5L));
        when(orderMapper.update(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(1);

        service.changeStatus(5L, "PENDING", "渠道受理", "CHANNEL:MOCK_WALLET");

        ArgumentCaptor<PaymentOrderStateLog> logCaptor = ArgumentCaptor.forClass(PaymentOrderStateLog.class);
        verify(stateLogMapper).insert(logCaptor.capture());
        assertEquals("CREATED", logCaptor.getValue().getFromStatus());
        assertEquals("PENDING", logCaptor.getValue().getToStatus());
        assertEquals("渠道受理", logCaptor.getValue().getReason());
        assertEquals("CHANNEL:MOCK_WALLET", logCaptor.getValue().getOperator());
        // S16：状态推进成功后按目标状态发事件（实体引用不经重载比较，按类型匹配）
        verify(paymentEventPublisher).publishFor(
                org.mockito.ArgumentMatchers.any(PaymentOrder.class),
                org.mockito.ArgumentMatchers.eq("PENDING"), org.mockito.ArgumentMatchers.eq("渠道受理"));
    }

    @Test
    void changeStatusRejectsIllegalTransition() {
        when(orderMapper.selectById(5L)).thenReturn(order(5L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changeStatus(5L, "PAID", "跳步推进", "test"));

        assertEquals(PaymentErrorCode.ILLEGAL_STATUS_TRANSITION, error.getErrorCode());
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void changeStatusRejectsWhenCasRaceDetected() {
        when(orderMapper.selectById(5L)).thenReturn(order(5L));
        // CAS where 旧状态更新 0 行：状态已被并发修改
        when(orderMapper.update(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changeStatus(5L, "PENDING", "并发推进", "test"));

        assertEquals(PaymentErrorCode.ILLEGAL_STATUS_TRANSITION, error.getErrorCode());
        verify(stateLogMapper, never()).insert(any(PaymentOrderStateLog.class));
        verify(paymentEventPublisher, never()).publishFor(any(), any(), any());
    }

    @Test
    void getDetailReturnsOrderWithStateLogs() {
        when(orderMapper.selectById(5L)).thenReturn(order(5L));
        when(stateLogMapper.selectList(any())).thenReturn(List.of(stateLog(5L)));

        var detail = service.getDetail(5L);

        assertEquals("P20261017000001", detail.order().platformOrderNo());
        assertEquals(1, detail.stateLogs().size());
        assertEquals("CREATED", detail.stateLogs().get(0).fromStatus());
        assertEquals("PENDING", detail.stateLogs().get(0).toStatus());
    }

    @Test
    void getDetailRejectsUnknownOrder() {
        when(orderMapper.selectById(9L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.getDetail(9L));

        assertEquals(PaymentErrorCode.ORDER_NOT_FOUND, error.getErrorCode());
    }

    private PaymentOrderCreateRequest request(Long merchantId, LocalDateTime expiredAt) {
        return new PaymentOrderCreateRequest(merchantId, "MO-20261017001", 10000L,
                null, "测试商品", null, "ALIPAY", null, expiredAt, null, null);
    }

    private MerchantDetailResponse merchantDetail(String status) {
        MerchantResponse merchant = new MerchantResponse(1L, "M20261017000001", "测试商户", null, "张三",
                "13800000000", null, "91110000MA01ABCD0X", null, "T1", null, status, LocalDateTime.now());
        return new MerchantDetailResponse(merchant, List.of());
    }

    private PaymentOrder order(Long id) {
        PaymentOrder order = new PaymentOrder();
        order.setId(id);
        order.setMerchantId(1L);
        order.setMerchantOrderNo("MO-20261017001");
        order.setPlatformOrderNo("P20261017000001");
        order.setAmount(10000L);
        order.setCurrency("CNY");
        order.setSubject("测试商品");
        order.setPaymentMethod("ALIPAY");
        order.setExpiredAt(LocalDateTime.now().plusHours(2));
        order.setStatus("CREATED");
        return order;
    }

    private PaymentOrderStateLog stateLog(Long orderId) {
        PaymentOrderStateLog log = new PaymentOrderStateLog();
        log.setId(1L);
        log.setOrderId(orderId);
        log.setFromStatus("CREATED");
        log.setToStatus("PENDING");
        log.setReason("受理支付");
        log.setOperator("system");
        return log;
    }
}
