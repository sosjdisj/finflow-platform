package org.example.finflowbackend.merchant.service.impl;

import org.example.finflowbackend.audit.AuditRecorder;
import org.example.finflowbackend.auth.token.CurrentUser;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.merchant.MerchantErrorCode;
import org.example.finflowbackend.merchant.dto.MerchantDetailResponse;
import org.example.finflowbackend.merchant.dto.MerchantRateSetRequest;
import org.example.finflowbackend.merchant.entity.MerchantRate;
import org.example.finflowbackend.merchant.entity.MerchantRateLog;
import org.example.finflowbackend.merchant.mapper.MerchantRateLogMapper;
import org.example.finflowbackend.merchant.mapper.MerchantRateMapper;
import org.example.finflowbackend.merchant.mapper.MerchantTierRateMapper;
import org.example.finflowbackend.merchant.service.MerchantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 费率单测：至少启用一种、变更历史含改前改后与操作人、未配置返回 null。
 */
class RateServiceImplTest {

    private final MerchantRateMapper rateMapper = mock(MerchantRateMapper.class);
    private final MerchantRateLogMapper rateLogMapper = mock(MerchantRateLogMapper.class);
    private final MerchantTierRateMapper tierRateMapper = mock(MerchantTierRateMapper.class);
    private final MerchantService merchantService = mock(MerchantService.class);
    private final RateServiceImpl service =
            new RateServiceImpl(rateMapper, rateLogMapper, tierRateMapper, merchantService,
                    new AuditRecorder(JsonMapper.builder().build()));

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setRejectsAllDisabledRate() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.set(1L, new MerchantRateSetRequest(null, null)));

        assertEquals(MerchantErrorCode.RATE_REQUIRED, error.getErrorCode());
    }

    @Test
    void setInsertsRateAndHistoryOnFirstConfig() {
        loginAsAdmin();
        when(rateMapper.selectOne(any())).thenReturn(null);

        var response = service.set(1L, new MerchantRateSetRequest(60L, 100L));

        assertEquals(60L, response.rateBps());
        assertEquals(100L, response.fixedFee());
        ArgumentCaptor<MerchantRateLog> captor = ArgumentCaptor.forClass(MerchantRateLog.class);
        verify(rateLogMapper).insert(captor.capture());
        MerchantRateLog logEntry = captor.getValue();
        assertNull(logEntry.getOldRateBps());
        assertEquals(60L, logEntry.getNewRateBps());
        assertEquals("admin", logEntry.getOperator());
    }

    @Test
    void setWritesOldValueHistoryOnUpdate() {
        loginAsAdmin();
        when(rateMapper.selectOne(any())).thenReturn(existingRate());

        var response = service.set(1L, new MerchantRateSetRequest(80L, null));

        assertEquals(80L, response.rateBps());
        ArgumentCaptor<MerchantRateLog> captor = ArgumentCaptor.forClass(MerchantRateLog.class);
        verify(rateLogMapper).insert(captor.capture());
        MerchantRateLog logEntry = captor.getValue();
        assertEquals(60L, logEntry.getOldRateBps());
        assertEquals(100L, logEntry.getOldFixedFee());
        assertEquals(80L, logEntry.getNewRateBps());
        assertNull(logEntry.getNewFixedFee());
    }

    @Test
    void getReturnsNullWhenNotConfigured() {
        when(rateMapper.selectOne(any())).thenReturn(null);

        assertNull(service.get(1L));
    }

    @Test
    void logsReturnInDescOrder() {
        when(rateLogMapper.selectList(any())).thenReturn(List.of(new MerchantRateLog(), new MerchantRateLog()));

        assertEquals(2, service.logs(1L).size());
        verify(rateLogMapper, times(1)).selectList(any());
    }

    @Test
    void setRejectsUnknownMerchant() {
        when(merchantService.getDetail(9L))
                .thenThrow(new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.set(9L, new MerchantRateSetRequest(60L, null)));

        assertEquals(MerchantErrorCode.MERCHANT_NOT_FOUND, error.getErrorCode());
        verify(rateMapper, never()).insert(any(MerchantRate.class));
    }

    private MerchantRate existingRate() {
        MerchantRate rate = new MerchantRate();
        rate.setId(1L);
        rate.setMerchantId(1L);
        rate.setRateBps(60L);
        rate.setFixedFee(100L);
        return rate;
    }

    private void loginAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CurrentUser(1L, "admin"), null));
    }
}
