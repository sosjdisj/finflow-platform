package org.example.finflowbackend.openapi;

import org.example.finflowbackend.common.tunable.TunableConfig;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.merchant.api.MerchantApi;
import org.example.finflowbackend.merchant.api.MerchantDetailResponse;
import org.example.finflowbackend.openapi.api.OpenApiErrorCode;
import org.example.finflowbackend.openapi.entity.MerchantOpenApiQuota;
import org.example.finflowbackend.openapi.mapper.MerchantOpenApiQuotaMapper;
import org.example.finflowbackend.openapi.quota.OpenApiApiCode;
import org.example.finflowbackend.openapi.quota.OpenApiQuotaService;
import org.example.finflowbackend.openapi.quota.OpenApiQuotaView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M12 配额单测：策略读表、计数走 Redis；停用 / QPS / 日配额三条拒绝路径与默认值回落。
 */
class OpenApiQuotaServiceTest {

    private final MerchantOpenApiQuotaMapper quotaMapper = mock(MerchantOpenApiQuotaMapper.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final MerchantApi merchantApi = mock(MerchantApi.class);
    private final MockEnvironment environment = new MockEnvironment()
            .withProperty(TunableConfig.OPENAPI_DEFAULT_QPS, "20")
            .withProperty(TunableConfig.OPENAPI_DEFAULT_DAILY, "50000");
    private final TunableConfig tunables = new TunableConfig(environment);
    private final OpenApiQuotaService service =
            new OpenApiQuotaService(quotaMapper, redisTemplate, tunables, merchantApi);

    @BeforeEach
    void stubRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void disabledApiIsRejectedBeforeCounting() {
        when(quotaMapper.selectOne(any())).thenReturn(quota(1L, OpenApiApiCode.PAYMENT_CREATE, 20, 5000L, false));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.check(1L, OpenApiApiCode.PAYMENT_CREATE));

        assertEquals(OpenApiErrorCode.OPENAPI_DISABLED, error.getErrorCode());
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void qpsOverLimitIsRejected() {
        // 未配置行 → 平台默认 20 次/秒；第 21 次在同一秒内
        when(quotaMapper.selectOne(any())).thenReturn(null);
        when(valueOperations.increment(anyString())).thenReturn(21L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.check(1L, OpenApiApiCode.PAYMENT_CREATE));

        assertEquals(OpenApiErrorCode.QUOTA_EXCEEDED, error.getErrorCode());
        assertTrue(error.getMessage().contains("每秒"));
    }

    @Test
    void dailyOverLimitIsRejectedAfterQpsPasses() {
        when(quotaMapper.selectOne(any())).thenReturn(quota(1L, OpenApiApiCode.PAYMENT_CREATE, 20, 10L, true));
        // 第一次（QPS 键）放行，第二次（日配额键）超限
        when(valueOperations.increment(anyString())).thenReturn(1L, 11L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.check(1L, OpenApiApiCode.PAYMENT_CREATE));

        assertEquals(OpenApiErrorCode.QUOTA_EXCEEDED, error.getErrorCode());
        assertTrue(error.getMessage().contains("每日"));
    }

    @Test
    void countsUseConfiguredLimitsAndExpireOnFirstIncrement() {
        when(quotaMapper.selectOne(any())).thenReturn(quota(1L, OpenApiApiCode.PAYMENT_CREATE, 100, 10_000L, true));
        when(valueOperations.increment(anyString())).thenReturn(1L);

        service.check(1L, OpenApiApiCode.PAYMENT_CREATE);

        verify(redisTemplate, times(2)).expire(anyString(), any(Duration.class));
    }

    @Test
    void listFillsDefaultsForUnconfiguredApis() {
        when(quotaMapper.selectList(any())).thenReturn(
                List.of(quota(1L, OpenApiApiCode.REFUND_CREATE, 0, 0L, true)));

        List<OpenApiQuotaView> views = service.list(1L);

        verify(merchantApi).getDetail(1L);
        assertEquals(OpenApiApiCode.values().length, views.size());
        OpenApiQuotaView configured = views.stream()
                .filter(view -> view.apiCode().equals(OpenApiApiCode.REFUND_CREATE.name()))
                .findFirst().orElseThrow();
        // 配置值 0 表示沿用平台默认
        assertEquals(0, configured.qpsLimit());
        assertEquals(20, configured.effectiveQpsLimit());
        assertEquals(50_000L, configured.effectiveDailyLimit());
        OpenApiQuotaView unconfigured = views.stream()
                .filter(view -> view.apiCode().equals(OpenApiApiCode.PAYMENT_QUERY.name()))
                .findFirst().orElseThrow();
        assertTrue(unconfigured.enabled());
    }

    @Test
    void updateInsertsWhenAbsentAndUpdatesWhenPresent() {
        when(quotaMapper.selectOne(any())).thenReturn(null);

        service.update(1L, "payment_create", 50, 5000L, false);

        ArgumentCaptor<MerchantOpenApiQuota> captor = ArgumentCaptor.forClass(MerchantOpenApiQuota.class);
        verify(quotaMapper).insert(captor.capture());
        assertEquals(OpenApiApiCode.PAYMENT_CREATE.name(), captor.getValue().getApiCode());
        assertFalse(captor.getValue().getEnabled());

        MerchantOpenApiQuota existing = quota(7L, OpenApiApiCode.PAYMENT_CREATE, 20, 1000L, true);
        when(quotaMapper.selectOne(any())).thenReturn(existing);

        service.update(1L, "PAYMENT_CREATE", 10, 2000L, true);

        verify(quotaMapper).updateById(existing);
        assertEquals(10, existing.getQpsLimit());
        assertEquals(2000L, existing.getDailyLimit());
    }

    @Test
    void updateRejectsUnknownApiCode() {
        when(merchantApi.getDetail(1L)).thenReturn(new MerchantDetailResponse(null, List.of()));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(1L, "UNKNOWN", 10, 100L, true));

        assertEquals(OpenApiErrorCode.PARAM_ERROR, error.getErrorCode());
        verify(quotaMapper, never()).insert(any(MerchantOpenApiQuota.class));
    }

    private MerchantOpenApiQuota quota(Long merchantId, OpenApiApiCode apiCode, Integer qps, Long daily,
                                       Boolean enabled) {
        MerchantOpenApiQuota quota = new MerchantOpenApiQuota();
        quota.setMerchantId(merchantId);
        quota.setApiCode(apiCode.name());
        quota.setQpsLimit(qps);
        quota.setDailyLimit(daily);
        quota.setEnabled(enabled);
        return quota;
    }
}
