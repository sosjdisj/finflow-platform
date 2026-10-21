package org.example.finflowbackend.system.service.impl;

import org.example.finflowbackend.auth.token.CurrentUser;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.system.SystemErrorCode;
import org.example.finflowbackend.system.config.ConfigKeys;
import org.example.finflowbackend.system.dto.ConfigUpdateRequest;
import org.example.finflowbackend.system.entity.SystemConfig;
import org.example.finflowbackend.system.mapper.SystemConfigMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 配置管理端单测（S37）：未登记 key 拒改、坏值拒改、合法修改落库 + 删缓存。
 */
class ConfigAdminServiceImplTest {

    private final SystemConfigMapper configMapper = mock(SystemConfigMapper.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ConfigAdminServiceImpl service =
            new ConfigAdminServiceImpl(configMapper, redisTemplate, mock());

    @BeforeEach
    void setUp() {
        when(configMapper.selectOne(any())).thenReturn(row(ConfigKeys.PAYMENT_SINGLE_LIMIT.key(), "10000000"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CurrentUser(2L, "admin"), null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unknownKeyIsRejected() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update("not.registered.key", new ConfigUpdateRequest("1", null)));

        assertEquals(SystemErrorCode.CONFIG_KEY_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void invalidValueIsRejected() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(ConfigKeys.PAYMENT_SINGLE_LIMIT.key(),
                        new ConfigUpdateRequest("abc", null)));

        assertEquals(SystemErrorCode.CONFIG_VALUE_INVALID, error.getErrorCode());
        verify(configMapper, never()).updateById(any(SystemConfig.class));
    }

    /** 合法修改：落库新值 + 删缓存键（刷新 = 失效重读）。 */
    @Test
    void updatePersistsValueAndEvictsCache() {
        service.update(ConfigKeys.PAYMENT_SINGLE_LIMIT.key(), new ConfigUpdateRequest("5000000", "新说明"));

        ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
        verify(configMapper).updateById(captor.capture());
        assertEquals("5000000", captor.getValue().getConfigValue());
        assertEquals("新说明", captor.getValue().getDescription());
        verify(redisTemplate).delete(ConfigServiceImpl.CACHE_KEY_PREFIX + ConfigKeys.PAYMENT_SINGLE_LIMIT.key());
    }

    @Test
    void listAllReturnsAllRowsSorted() {
        when(configMapper.selectList(any())).thenReturn(List.of(row(ConfigKeys.PAYMENT_SINGLE_LIMIT.key(), "1")));

        assertEquals(1, service.listAll().size());
    }

    private SystemConfig row(String key, String value) {
        SystemConfig config = new SystemConfig();
        config.setId(1L);
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setValueType(ConfigKeys.TYPE_NUMBER);
        config.setDescription("说明");
        config.setUpdatedBy(0L);
        return config;
    }
}
