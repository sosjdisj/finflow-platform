package org.example.finflowbackend.common.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Redis 分布式锁（S24）：SET NX EX + Lua 校验释放，防定时任务多实例并发重复跑。
 * 锁值存实例标识（UUID），释放时 Lua 比对仅删自己的锁，防误删他人锁；
 * 过期时间兜底实例崩溃不死锁。沿项目依赖最小化约定，不引入 Redisson。
 */
@Component
public class RedisLockService {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 持锁执行：拿到锁跑业务并 finally 释放；拿不到（他人持锁）执行 onSkip 并返回 null。
     *
     * @return action 的返回值；未拿到锁返回 null
     */
    public <T> T tryRun(String lockKey, Duration ttl, Supplier<T> action, Runnable onSkip) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, ttl);
        if (!Boolean.TRUE.equals(acquired)) {
            onSkip.run();
            return null;
        }
        try {
            return action.get();
        } finally {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(lockKey), token);
        }
    }
}
