package org.example.finflowbackend.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件信封（Kafka 线上报文格式，M04 起归 contract）：
 * eventId 供消费端幂等去重（单一事实源），eventType 供消费端分流，
 * version 为契约版本（payload 字段只增不改；破坏性变更须升版本并新建 payload 类型），
 * payload 为 {@link EventTopics} 对应主题的事件详情。
 *
 * <p>兼容约定：M04 之前的存量报文不带 version，反序列化时字段为 null，构造时归一为 0，
 * 消费端将其视为 v1 之前的报文。version 用包装类型仅为容忍缺省字段，实例中恒不为 null。</p>
 */
public record DomainEvent(String eventId, String eventType, Instant occurredAt, Integer version, Object payload) {

    /** 当前契约版本：新增字段不升版本，删除 / 改语义才升。 */
    public static final int CURRENT_VERSION = 1;

    public DomainEvent {
        version = version == null ? 0 : version;
    }

    public static DomainEvent of(String eventType, Object payload) {
        return new DomainEvent(UUID.randomUUID().toString(), eventType, Instant.now(), CURRENT_VERSION, payload);
    }
}
