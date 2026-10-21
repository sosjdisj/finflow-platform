package org.example.finflowbackend.ledger.event;

import org.example.finflowbackend.account.AccountOwnerType;
import org.example.finflowbackend.account.entity.Account;
import org.example.finflowbackend.account.service.AccountService;
import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.common.event.EventDeduplicationService;
import org.example.finflowbackend.ledger.service.LedgerService;
import org.example.finflowbackend.refund.event.RefundSucceededPayload;
import org.example.finflowbackend.refund.event.RefundTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 账务消费者：消费 RefundSucceeded 完成退款冲减。
 * 资金流约定（第一版，与 PaymentLedgerConsumer 对称）：退款成功 → 平台备付金账户 − 退款金额；
 * 商户的钱在结算前都在备付金，故退款直接冲减备付金（bizNo=refundNo，uk_biz 幂等）。
 * 幂等双保险：Redis 按 eventId 去重在前，流水表 uk_biz 唯一索引兜底在后。
 */
@Component
public class RefundLedgerConsumer {

    private static final Logger log = LoggerFactory.getLogger(RefundLedgerConsumer.class);

    /** 消费组（去重 key 前缀）；平台备付金账户 ownerId 约定为 0。 */
    static final String GROUP_ID = "finflow-ledger";
    private static final long PLATFORM_OWNER_ID = 0L;

    private final EventDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;
    private final AccountService accountService;
    private final LedgerService ledgerService;

    public RefundLedgerConsumer(EventDeduplicationService deduplicationService,
                                ObjectMapper objectMapper,
                                AccountService accountService,
                                LedgerService ledgerService) {
        this.deduplicationService = deduplicationService;
        this.objectMapper = objectMapper;
        this.accountService = accountService;
        this.ledgerService = ledgerService;
    }

    @KafkaListener(topics = RefundTopics.TOPIC_REFUND_SUCCEEDED, groupId = GROUP_ID)
    public void onMessage(String message) {
        handle(message);
    }

    /** 消费处理入口（包级可见便于测试）：去重 → 解析 payload → 备付金出账。 */
    void handle(String message) {
        DomainEvent event = objectMapper.readValue(message, DomainEvent.class);
        if (!deduplicationService.tryConsume(GROUP_ID, event.eventId())) {
            log.info("[退款账务消费者] 重复事件跳过: eventId={}", event.eventId());
            return;
        }
        RefundSucceededPayload payload =
                objectMapper.convertValue(event.payload(), RefundSucceededPayload.class);
        Account account = accountService.getOrCreate(
                AccountOwnerType.PLATFORM, PLATFORM_OWNER_ID, payload.currency());
        try {
            ledgerService.debit(account.getId(), "REFUND", payload.refundNo(),
                    payload.refundAmount(), "REFUND_EVENT", "退款冲减: " + payload.platformOrderNo());
        } catch (DuplicateKeyException e) {
            // 库层幂等兜底：Redis 去重窗口外的重投，同退款单流水已存在，视为已出账
            log.info("[退款账务消费者] 流水已存在，跳过重复出账: refundNo={}", payload.refundNo());
        }
        log.info("[退款账务消费者] 退款冲减完成: refundNo={}, amount={}",
                payload.refundNo(), payload.refundAmount());
    }
}
