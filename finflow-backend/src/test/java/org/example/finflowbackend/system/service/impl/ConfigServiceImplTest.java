package org.example.finflowbackend.system.service.impl;

import org.example.finflowbackend.system.config.ConfigKeys;
import org.example.finflowbackend.system.entity.SystemConfig;
import org.example.finflowbackend.system.mapper.SystemConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配置读取单测（S37）：缓存命中不查库、未命中回源并写缓存、缺行回退默认值、
 * 坏值解析回退默认值、布尔解析。
 */
class ConfigServiceImplTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final SystemConfigMapper configMapper = mock(SystemConfigMapper.class);
    private final ConfigServiceImpl service = new ConfigServiceImpl(configMapper, redisTemplate);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void cacheHitSkipsDatabase() {
        when(valueOps.get(ConfigServiceImpl.CACHE_KEY_PREFIX + ConfigKeys.PAYMENT_ORDER_EXPIRE_HOURS.key()))
                .thenReturn("3");

        assertEquals(3L, service.getLong(ConfigKeys.PAYMENT_ORDER_EXPIRE_HOURS));
        verify(configMapper, times(0)).selectOne(any());
    }

    @Test
    void cacheMissLoadsFromDbAndFillsCache() {
        when(valueOps.get(any())).thenReturn(null);
        when(configMapper.selectOne(any())).thenReturn(row("payment.order.expire-hours", "6"));

        assertEquals(6L, service.getLong(ConfigKeys.PAYMENT_ORDER_EXPIRE_HOURS));
        verify(valueOps).set(eq(ConfigServiceImpl.CACHE_KEY_PREFIX + ConfigKeys.PAYMENT_ORDER_EXPIRE_HOURS.key()),
                eq("6"), eq(Duration.ofMinutes(10)));
    }

    /** 缺行回退定义默认值（seed 保证有行，此处防御手工删数据）。 */
    @Test
    void missingRowFallsBackToDefinedDefault() {
        when(valueOps.get(any())).thenReturn(null);
        when(configMapper.selectOne(any())).thenReturn(null);

        assertEquals(10_000_000L, service.getLong(ConfigKeys.PAYMENT_SINGLE_LIMIT));
    }

    /** 坏值不阻断业务：解析失败回退默认值。 */
    @Test
    void corruptValueFallsBackToDefault() {
        when(valueOps.get(any())).thenReturn("not-a-number");

        assertEquals(2L, service.getLong(ConfigKeys.PAYMENT_ORDER_EXPIRE_HOURS));
    }

    @Test
    void boolParsesTrueAndFalse() {
        when(valueOps.get(any())).thenReturn(null);
        when(configMapper.selectOne(any())).thenReturn(row("switch.payment.create", "false"));

        assertFalse(service.getBool(ConfigKeys.SWITCH_PAYMENT_CREATE));

        when(configMapper.selectOne(any())).thenReturn(row("switch.payment.create", "true"));
        assertTrue(service.getBool(ConfigKeys.SWITCH_PAYMENT_CREATE));
    }

    private SystemConfig row(String key, String value) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        return config;
    }
}
