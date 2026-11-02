package org.example.finflowbackend.ledger.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.finflowbackend.account.api.AccountApi;
import org.example.finflowbackend.account.api.AccountOwnerType;
import org.example.finflowbackend.account.api.AccountSnapshot;
import org.example.finflowbackend.common.event.EventDeduplicationService;
import org.example.finflowbackend.contract.DomainEvent;
import org.example.finflowbackend.contract.EventTopics;
import org.example.finflowbackend.contract.PaymentSucceededPayload;
import org.example.finflowbackend.ledger.service.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 账务消费者：消费 PaymentSucceeded 完成入账。
 * 资金流约定（第一版）：支付成功 → 平台备付金账户（PLATFORM, ownerId=0, 按币种）+ 订单全额；
 * 手续费在清分阶段只算不动账，结算阶段的资金流由结算阶段实现。
 * 幂等双保险：Redis 按 eventId 去重在前，流水表 uk_biz 唯一索引兜底在后。
 */
@Component
public class PaymentLedgerConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentLedgerConsumer.class);

    /** 消费组（去重 key 前缀）；平台备付金账户 ownerId 约定为 0。 */
    static final String GROUP_ID = "finflow-ledger";
    private static final long PLATFORM_OWNER_ID = 0L;

    private final EventDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;
    private final AccountApi accountApi;
    private final LedgerService ledgerService;

    public PaymentLedgerConsumer(EventDeduplicationService deduplicationService,
                                 ObjectMapper objectMapper,
                                 AccountApi accountApi,
                                 LedgerService ledgerService) {
        this.deduplicationService = deduplicationService;
        this.objectMapper = objectMapper;
        this.accountApi = accountApi;
        this.ledgerService = ledgerService;
    }

    @KafkaListener(topics = EventTopics.PAYMENT_SUCCEEDED, groupId = GROUP_ID)
    public void onMessage(String message) {
        handle(message);
    }

    /** 消费处理入口（包级可见便于测试）：去重 → 解析 payload → 备付金入账。 */
    void handle(String message) {
        DomainEvent event = objectMapper.readValue(message, DomainEvent.class);
        if (!deduplicationService.tryConsume(GROUP_ID, event.eventId())) {
            log.info("[账务消费者] 重复事件跳过: eventId={}", event.eventId());
            return;
        }
        PaymentSucceededPayload payload =
                objectMapper.convertValue(event.payload(), PaymentSucceededPayload.class);
        AccountSnapshot account = accountApi.getOrCreate(
                AccountOwnerType.PLATFORM, PLATFORM_OWNER_ID, payload.currency());
        try {
            ledgerService.credit(account.id(), "PAYMENT", payload.platformOrderNo(),
                    payload.amount(), "PAYMENT_EVENT");
        } catch (DuplicateKeyException e) {
            // 库层幂等兜底：Redis 去重窗口外的重投，同订单流水已存在，视为已入账
            log.info("[账务消费者] 流水已存在，跳过重复入账: orderNo={}", payload.platformOrderNo());
        }
        log.info("[账务消费者] 支付成功入账完成: orderNo={}, amount={}",
                payload.platformOrderNo(), payload.amount());
    }
}
