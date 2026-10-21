package org.example.finflowbackend.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.common.event.EventDeduplicationService;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.notification.service.NotificationService;
import org.example.finflowbackend.payment.dto.PaymentOrderResponse;
import org.example.finflowbackend.payment.event.PaymentSucceededPayload;
import org.example.finflowbackend.payment.event.PaymentTopics;
import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.example.finflowbackend.reconciliation.event.ReconciliationAbnormalPayload;
import org.example.finflowbackend.reconciliation.event.ReconciliationTopics;
import org.example.finflowbackend.refund.event.RefundSucceededPayload;
import org.example.finflowbackend.refund.event.RefundTopics;
import org.example.finflowbackend.risk.event.RiskInterceptedPayload;
import org.example.finflowbackend.risk.event.RiskTopics;
import org.example.finflowbackend.settlement.event.SettlementSucceededPayload;
import org.example.finflowbackend.settlement.event.SettlementTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通知事件消费者（S33）：订阅支付成功 / 退款成功 / 结算完成 / 风控拦截 / 对账异常五类事件，
 * 渲染模板并发送通知。风控拦截通知商户（回查订单取商户与平台单号）；对账异常为平台通知（USER 收件）。
 * 幂等：Redis 按 eventId 去重在前，notification.uk(event_id) 兜底在后。
 * 事实源：支付 / 风控事件以回查订单取商户为准（防 payload 陈旧，沿清分 / Webhook 消费者惯例）；
 * 退款 / 结算 / 对账事件 payload 自带归属。模板缺失 / 商户缺失只记日志，不阻断消费。
 */
@Component
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    static final String GROUP_ID = "finflow-notification";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 差异类型 → 通知展示文案（模板 {{differenceType}} 渲染用）。 */
    private static final Map<String, String> DIFFERENCE_LABELS = Map.of(
            "AMOUNT_MISMATCH", "金额不一致",
            "STATUS_MISMATCH", "状态不一致",
            "MISSING_IN_PLATFORM", "平台缺单",
            "MISSING_IN_CHANNEL", "渠道缺单");

    private final EventDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;
    private final PaymentOrderService paymentOrderService;
    private final NotificationService notificationService;

    public NotificationEventConsumer(EventDeduplicationService deduplicationService,
                                     ObjectMapper objectMapper,
                                     PaymentOrderService paymentOrderService,
                                     NotificationService notificationService) {
        this.deduplicationService = deduplicationService;
        this.objectMapper = objectMapper;
        this.paymentOrderService = paymentOrderService;
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = PaymentTopics.TOPIC_SUCCEEDED, groupId = GROUP_ID)
    public void onPaymentSucceeded(String message) {
        handle(message, event -> {
            PaymentSucceededPayload payload =
                    objectMapper.convertValue(event.payload(), PaymentSucceededPayload.class);
            PaymentOrderResponse order = paymentOrderService.get(payload.orderId());
            Map<String, String> params = new LinkedHashMap<>();
            params.put("platformOrderNo", payload.platformOrderNo());
            params.put("amount", fenToYuan(payload.amount()));
            params.put("currency", payload.currency());
            params.put("paidAt", fmt(payload.paidAt()));
            return Notice.toMerchant("PAYMENT_SUCCEEDED", order.merchantId(), params);
        });
    }

    @KafkaListener(topics = RefundTopics.TOPIC_REFUND_SUCCEEDED, groupId = GROUP_ID)
    public void onRefundSucceeded(String message) {
        handle(message, event -> {
            RefundSucceededPayload payload =
                    objectMapper.convertValue(event.payload(), RefundSucceededPayload.class);
            Map<String, String> params = new LinkedHashMap<>();
            params.put("refundNo", payload.refundNo());
            params.put("platformOrderNo", payload.platformOrderNo());
            params.put("refundAmount", fenToYuan(payload.refundAmount()));
            params.put("currency", payload.currency());
            params.put("refundedAt", fmt(payload.refundedAt()));
            return Notice.toMerchant("REFUND_SUCCEEDED", payload.merchantId(), params);
        });
    }

    @KafkaListener(topics = SettlementTopics.TOPIC_SETTLEMENT_SUCCEEDED, groupId = GROUP_ID)
    public void onSettlementSucceeded(String message) {
        handle(message, event -> {
            SettlementSucceededPayload payload =
                    objectMapper.convertValue(event.payload(), SettlementSucceededPayload.class);
            Map<String, String> params = new LinkedHashMap<>();
            params.put("settleNo", payload.settleNo());
            params.put("settleDate", fmt(payload.settleDate()));
            params.put("totalAmount", fenToYuan(payload.totalAmount()));
            params.put("currency", payload.currency());
            params.put("executedAt", fmt(payload.executedAt()));
            return Notice.toMerchant("SETTLEMENT_SUCCEEDED", payload.merchantId(), params);
        });
    }

    @KafkaListener(topics = RiskTopics.TOPIC_INTERCEPTED, groupId = GROUP_ID)
    public void onRiskIntercepted(String message) {
        handle(message, event -> {
            RiskInterceptedPayload payload =
                    objectMapper.convertValue(event.payload(), RiskInterceptedPayload.class);
            // 商户与平台单号以回查订单为准（payload 可能陈旧）
            PaymentOrderResponse order = paymentOrderService.get(payload.orderId());
            Map<String, String> params = new LinkedHashMap<>();
            params.put("platformOrderNo", order.platformOrderNo());
            params.put("action", "REVIEW".equals(payload.verdict()) ? "拦截审核" : "拒绝");
            params.put("score", String.valueOf(payload.totalScore()));
            params.put("reasons", payload.reason());
            return Notice.toMerchant("RISK_INTERCEPTED", order.merchantId(), params);
        });
    }

    @KafkaListener(topics = ReconciliationTopics.TOPIC_ABNORMAL, groupId = GROUP_ID)
    public void onReconciliationAbnormal(String message) {
        handle(message, event -> {
            ReconciliationAbnormalPayload payload =
                    objectMapper.convertValue(event.payload(), ReconciliationAbnormalPayload.class);
            Map<String, String> params = new LinkedHashMap<>();
            params.put("taskNo", payload.taskNo());
            params.put("differenceType", differenceLabel(payload.differenceType()));
            params.put("platformAmount", fenToYuan(payload.platformAmount()));
            params.put("channelAmount", fenToYuan(payload.channelAmount()));
            return Notice.toPlatform("RECONCILIATION_DIFFERENCE", params);
        });
    }

    /**
     * 事件处理入口（公开便于测试直调）：去重 → 组参数 → 发送。
     * Notice.merchantId 为空即平台通知，非空走商户通知。
     */
    public void handle(String message, NoticeResolver resolver) {
        DomainEvent event = objectMapper.readValue(message, DomainEvent.class);
        if (!deduplicationService.tryConsume(GROUP_ID, event.eventId())) {
            log.info("[通知消费者] 重复事件跳过: eventId={}", event.eventId());
            return;
        }
        Notice notice = resolver.resolve(event);
        try {
            if (notice.merchantId() == null) {
                notificationService.sendPlatformNotification(notice.templateCode(), event.eventId(),
                        notice.params());
            } else {
                notificationService.sendMerchantNotification(notice.templateCode(), event.eventId(),
                        notice.merchantId(), notice.params());
            }
        } catch (BusinessException e) {
            // 模板缺失等可预期问题：记日志跳过，避免 Kafka 无限重投
            log.warn("[通知消费者] 通知发送被跳过: eventId={}, reason={}", event.eventId(), e.getMessage());
        }
    }

    /** 展示时间格式：日期时间 → 2026-10-18 15:30:00；日期 → 2026-10-18。 */
    private String fmt(LocalDateTime dateTime) {
        return TIME_FMT.format(dateTime);
    }

    private String fmt(LocalDate date) {
        return DATE_FMT.format(date);
    }

    /** 金额「分」→「元」展示字符串（仅格式化展示，不参与任何计算）。 */
    private String fenToYuan(Long fen) {
        return BigDecimal.valueOf(fen, 2).toPlainString();
    }

    private String differenceLabel(String differenceType) {
        return DIFFERENCE_LABELS.getOrDefault(differenceType, differenceType);
    }

    /** 通知意图：模板编码 + 收件商户（空 = 平台通知）+ 渲染参数。 */
    record Notice(String templateCode, Long merchantId, Map<String, String> params) {

        static Notice toMerchant(String templateCode, Long merchantId, Map<String, String> params) {
            return new Notice(templateCode, merchantId, params);
        }

        static Notice toPlatform(String templateCode, Map<String, String> params) {
            return new Notice(templateCode, null, params);
        }
    }

    @FunctionalInterface
    public interface NoticeResolver {
        Notice resolve(DomainEvent event);
    }
}
