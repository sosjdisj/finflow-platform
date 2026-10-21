package org.example.finflowbackend.settlement;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.example.finflowbackend.clearing.entity.ClearingOrder;
import org.example.finflowbackend.clearing.mapper.ClearingOrderMapper;
import org.example.finflowbackend.common.lock.RedisLockService;
import org.example.finflowbackend.settlement.service.SettlementGenerateService;
import org.example.finflowbackend.settlement.service.SettlementOrderWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结算生成编排单测：持锁分组委托、无清分跳过、锁被占跳过。
 */
class SettlementGenerateServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 17);

    private ClearingOrderMapper clearingOrderMapper;
    private SettlementOrderWriter orderWriter;
    private RedisLockService redisLockService;
    private SettlementGenerateService service;

    @BeforeEach
    void setUp() {
        clearingOrderMapper = mock(ClearingOrderMapper.class);
        orderWriter = mock(SettlementOrderWriter.class);
        redisLockService = mock(RedisLockService.class);
        service = new SettlementGenerateService(clearingOrderMapper, orderWriter, redisLockService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generatesPerMerchantUnderLock() {
        when(redisLockService.tryRun(any(), any(), any(), any())).thenAnswer(inv -> {
            ((Supplier<Integer>) inv.getArgument(2)).get();
            return 2;
        });
        when(clearingOrderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                clearing(1L, 10L, 3000L), clearing(2L, 10L, 2000L), clearing(3L, 11L, 5000L)));
        when(orderWriter.generateOne(eq(10L), eq(DATE), any())).thenReturn(true);
        when(orderWriter.generateOne(eq(11L), eq(DATE), any())).thenReturn(true);

        int generated = service.generateFor(DATE);

        assertEquals(2, generated);
        verify(orderWriter).generateOne(eq(10L), eq(DATE), any());
        verify(orderWriter).generateOne(eq(11L), eq(DATE), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void skipsWhenNoClearedRecords() {
        when(redisLockService.tryRun(any(), any(), any(), any())).thenAnswer(inv -> {
            ((Supplier<Integer>) inv.getArgument(2)).get();
            return 0;
        });
        when(clearingOrderMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        int generated = service.generateFor(DATE);

        assertEquals(0, generated);
        verify(orderWriter, never()).generateOne(any(), any(), any());
    }

    @Test
    void skipsWhenLockHeldByOtherInstance() {
        when(redisLockService.tryRun(any(), any(), any(), any())).thenReturn(null);

        int generated = service.generateFor(DATE);

        assertEquals(0, generated);
        verify(clearingOrderMapper, never()).selectList(any());
        verify(orderWriter, never()).generateOne(any(), any(), any());
    }

    private ClearingOrder clearing(long id, long merchantId, long merchantIncome) {
        ClearingOrder clearing = new ClearingOrder();
        clearing.setId(id);
        clearing.setOrderId(1000L + id);
        clearing.setMerchantId(merchantId);
        clearing.setPayAmount(merchantIncome + 100);
        clearing.setMerchantIncome(merchantIncome);
        clearing.setPlatformFee(50L);
        clearing.setChannelCost(50L);
        clearing.setCurrency("CNY");
        clearing.setChannelCode("MOCK_WALLET");
        clearing.setSettleDate(DATE);
        clearing.setStatus("CLEARED");
        return clearing;
    }
}
