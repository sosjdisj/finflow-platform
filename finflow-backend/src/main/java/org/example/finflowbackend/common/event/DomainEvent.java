package org.example.finflowbackend.common.event;

import java.time.Instant;

/**
 * 领域事件信封：eventId 供消费端幂等去重（单一事实源），
 * eventType 供消费端分流，payload 为模块自定义事件详情（JSON 序列化传输）。
 */
public record DomainEvent(String eventId, String eventType, Instant occurredAt, Object payload) {

    public static DomainEvent of(String eventType, Object payload) {
        return new DomainEvent(java.util.UUID.randomUUID().toString(), eventType, Instant.now(), payload);
    }
}
