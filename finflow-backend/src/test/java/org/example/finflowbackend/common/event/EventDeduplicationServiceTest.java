package org.example.finflowbackend.common.event;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 消费幂等工具单测：SETNX 语义映射首次/重复，key 与 TTL 正确。
 */
class EventDeduplicationServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private final EventDeduplicationService service = new EventDeduplicationService(redisTemplate);

    @Test
    void firstConsumeReturnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("event:consumed:grp-a:evt-1"), eq("1"), eq(Duration.ofHours(24))))
                .thenReturn(true);

        assertTrue(service.tryConsume("grp-a", "evt-1"));
    }

    @Test
    void duplicateConsumeReturnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("event:consumed:grp-a:evt-2"), eq("1"), eq(Duration.ofHours(24))))
                .thenReturn(false);

        assertFalse(service.tryConsume("grp-a", "evt-2"));
    }

    @Test
    void deduplicationIsolatedByConsumerGroup() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // 同一事件不同消费组使用不同 key，互不抢注
        when(valueOps.setIfAbsent(eq("event:consumed:grp-a:evt-3"), eq("1"), eq(Duration.ofHours(24))))
                .thenReturn(true);
        when(valueOps.setIfAbsent(eq("event:consumed:grp-b:evt-3"), eq("1"), eq(Duration.ofHours(24))))
                .thenReturn(true);

        assertTrue(service.tryConsume("grp-a", "evt-3"));
        assertTrue(service.tryConsume("grp-b", "evt-3"));
    }
}
