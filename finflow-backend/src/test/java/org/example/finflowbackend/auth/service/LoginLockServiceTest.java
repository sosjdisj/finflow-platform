package org.example.finflowbackend.auth.service;

import org.example.finflowbackend.auth.AuthErrorCode;
import org.example.finflowbackend.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录锁定单测：计数达阈值拒绝、首次失败设 TTL、成功清零。
 */
class LoginLockServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final LoginLockService service = new LoginLockService(redisTemplate);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void rejectsLoginWhenAttemptsReached() {
        when(valueOperations.get("login:fail:alice")).thenReturn("5");

        BusinessException error = assertThrows(BusinessException.class, () -> service.checkLocked("alice"));

        assertEquals(AuthErrorCode.LOGIN_LOCKED, error.getErrorCode());
    }

    @Test
    void allowsLoginBelowThreshold() {
        when(valueOperations.get("login:fail:alice")).thenReturn("4");

        assertDoesNotThrow(() -> service.checkLocked("alice"));
    }

    @Test
    void allowsLoginWithoutCounter() {
        when(valueOperations.get("login:fail:alice")).thenReturn(null);

        assertDoesNotThrow(() -> service.checkLocked("alice"));
    }

    @Test
    void firstFailureSetsTtl() {
        when(valueOperations.increment("login:fail:alice")).thenReturn(1L);

        service.recordFailure("alice");

        verify(redisTemplate).expire("login:fail:alice", Duration.ofMinutes(15));
    }

    @Test
    void laterFailureDoesNotResetTtl() {
        when(valueOperations.increment("login:fail:alice")).thenReturn(3L);

        service.recordFailure("alice");

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void clearDeletesCounter() {
        service.clear("alice");

        verify(redisTemplate).delete("login:fail:alice");
    }

    @Test
    void lockedErrorMapsTo429() {
        assertEquals(429, AuthErrorCode.LOGIN_LOCKED.getHttpStatus());
    }
}
