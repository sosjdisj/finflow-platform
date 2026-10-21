package org.example.finflowbackend.auth.service;

import org.example.finflowbackend.auth.AuthErrorCode;
import org.example.finflowbackend.auth.dto.CaptchaResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证码单测：生成落 Redis、一次性校验（取出即删）、忽略大小写、错误/过期拒绝。
 */
class CaptchaServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final CaptchaService service = new CaptchaService(redisTemplate);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void generateReturnsBase64ImageAndStoresAnswer() {
        CaptchaResponse response = service.generate();

        assertNotNull(response.captchaId());
        assertTrue(response.imageBase64().startsWith("data:image/png;base64,"));
        verify(valueOperations).set(eq(CaptchaService.KEY_PREFIX + response.captchaId()), anyString(),
                eq(CaptchaService.TTL));
    }

    @Test
    void verifyAcceptsCorrectCodeIgnoringCase() {
        when(valueOperations.getAndDelete("captcha:id-1")).thenReturn("AB2D");

        assertDoesNotThrow(() -> service.verify("id-1", "ab2d"));
    }

    @Test
    void verifyRejectsWrongCode() {
        when(valueOperations.getAndDelete("captcha:id-1")).thenReturn("AB2D");

        BusinessException error = assertThrows(BusinessException.class, () -> service.verify("id-1", "XXXX"));

        assertEquals(AuthErrorCode.CAPTCHA_INVALID, error.getErrorCode());
    }

    @Test
    void verifyRejectsExpiredOrMissingCode() {
        when(valueOperations.getAndDelete("captcha:id-1")).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.verify("id-1", "AB2D"));

        assertEquals(AuthErrorCode.CAPTCHA_INVALID, error.getErrorCode());
    }
}
