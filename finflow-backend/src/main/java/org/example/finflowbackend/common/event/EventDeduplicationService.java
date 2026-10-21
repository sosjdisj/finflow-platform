package org.example.finflowbackend.common.event;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 事件消费幂等工具：消费端按（消费组, eventId）在 Redis 去重（SETNX，TTL 覆盖重投窗口）。
 * 约定：所有消费者处理业务前先 tryConsume，返回 false 说明本组已处理过，直接跳过。
 * 去重按消费组隔离：同一事件各消费组都要各自处理一次，互不抢注。
 */
@Component
public class EventDeduplicationService {

    private static final String KEY_PREFIX = "event:consumed:";
    /** 去重保留时长：覆盖 Kafka 重投与消费者重启窗口。 */
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public EventDeduplicationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** true=本组首次消费（调用方继续处理）；false=本组重复投递（调用方跳过）。 */
    public boolean tryConsume(String consumerGroup, String eventId) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + consumerGroup + ":" + eventId, "1", TTL));
    }
}
