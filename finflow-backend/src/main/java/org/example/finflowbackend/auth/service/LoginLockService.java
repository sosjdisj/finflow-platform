package org.example.finflowbackend.auth.service;

import org.example.finflowbackend.auth.AuthErrorCode;
import org.example.finflowbackend.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败锁定：Redis 计数，同一用户名连续错 5 次锁 15 分钟，登录成功清零。
 * 计数 key 带 TTL 自然过期，无需清理任务。
 */
@Service
public class LoginLockService {

    static final String KEY_PREFIX = "login:fail:";
    static final int MAX_ATTEMPTS = 5;
    static final Duration LOCK_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    public LoginLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 锁定中则直接拒绝登录。 */
    public void checkLocked(String username) {
        String count = redisTemplate.opsForValue().get(KEY_PREFIX + username);
        if (count != null && Integer.parseInt(count) >= MAX_ATTEMPTS) {
            throw new BusinessException(AuthErrorCode.LOGIN_LOCKED);
        }
    }

    /** 记一次密码错误：首次失败设置 TTL，之后只累加计数、不重置窗口。 */
    public void recordFailure(String username) {
        String key = KEY_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, LOCK_TTL);
        }
    }

    /** 登录成功后清零。 */
    public void clear(String username) {
        redisTemplate.delete(KEY_PREFIX + username);
    }
}
