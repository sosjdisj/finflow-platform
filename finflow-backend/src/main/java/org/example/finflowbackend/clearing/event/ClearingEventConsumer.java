package org.example.finflowbackend.clearing.event;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.clearing.entity.ClearingDetail;
import org.example.finflowbackend.clearing.entity.ClearingOrder;
import org.example.finflowbackend.clearing.mapper.ClearingDetailMapper;
import org.example.finflowbackend.clearing.mapper.ClearingOrderMapper;
import org.example.finflowbackend.clearing.service.FeeCalculator;
import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.common.event.EventDeduplicationService;
import org.example.finflowbackend.merchant.dto.ClearingFeeRateView;
import org.example.finflowbackend.merchant.service.RateService;
import org.example.finflowbackend.payment.dto.PaymentOrderResponse;
import org.example.finflowbackend.payment.event.PaymentSucceededPayload;
import org.example.finflowbackend.payment.event.PaymentTopics;
import org.example.finflowbackend.payment.service.ChannelConfigService;
import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.example.finflowbackend.payment.service.PaymentTransactionService;
import org.example.finflowbackend.system.config.ConfigKeys;
import org.example.finflowbackend.system.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 清分消费者（S23）：消费 PaymentSucceeded 生成清分单 + 三行分账明细。
 * 只计算记账，不动任何账户余额；资金流约定：支付成功 → 备付金 + 全额（S19 已做），
 * 商户入账在结算执行（S25）。
 * 幂等双保险：Redis 按 eventId 去重在前，clearing_order.uk(order_id) 兜底在后；
 * 订单/商户/渠道/费率经 payment、merchant 模块 Service 读取（铁律 4），payload 金额仅作回查依据。
 */
@Component
public class ClearingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClearingEventConsumer.class);

    /** 消费组（去重 key 前缀）。 */
    static final String GROUP_ID = "finflow-clearing";

    /** 渠道码缺失时的占位（渠道成本按 0 计）。 */
    private static final String UNKNOWN_CHANNEL = "UNKNOWN";

    private final EventDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;
    private final ClearingOrderMapper clearingOrderMapper;
    private final ClearingDetailMapper clearingDetailMapper;
    private final FeeCalculator feeCalculator;
    private final PaymentOrderService paymentOrderService;
    private final PaymentTransactionService paymentTransactionService;
    private final ChannelConfigService channelConfigService;
    private final RateService rateService;
    private final ConfigService configService;

    public ClearingEventConsumer(EventDeduplicationService deduplicationService,
                                 ObjectMapper objectMapper,
                                 ClearingOrderMapper clearingOrderMapper,
                                 ClearingDetailMapper clearingDetailMapper,
                                 FeeCalculator feeCalculator,
                                 PaymentOrderService paymentOrderService,
                                 PaymentTransactionService paymentTransactionService,
                                 ChannelConfigService channelConfigService,
                                 RateService rateService, ConfigService configService) {
        this.deduplicationService = deduplicationService;
        this.objectMapper = objectMapper;
        this.clearingOrderMapper = clearingOrderMapper;
        this.clearingDetailMapper = clearingDetailMapper;
        this.feeCalculator = feeCalculator;
        this.paymentOrderService = paymentOrderService;
        this.paymentTransactionService = paymentTransactionService;
        this.channelConfigService = channelConfigService;
        this.rateService = rateService;
        this.configService = configService;
    }

    /** 1 单 + 3 明细需原子落库：@Transactional 放在监听方法（容器经代理调用）。 */
    @KafkaListener(topics = PaymentTopics.TOPIC_SUCCEEDED, groupId = GROUP_ID)
    @Transactional
    public void onMessage(String message) {
        handle(message);
    }

    /** 消费处理入口（公开便于测试直调）。 */
    public void handle(String message) {
        DomainEvent event = objectMapper.readValue(message, DomainEvent.class);
        if (!deduplicationService.tryConsume(GROUP_ID, event.eventId())) {
            log.info("[清分消费者] 重复事件跳过: eventId={}", event.eventId());
            return;
        }
        PaymentSucceededPayload payload =
                objectMapper.convertValue(event.payload(), PaymentSucceededPayload.class);
        // 以订单为准（防 payload 陈旧/历史消息缺字段），商户与金额回查
        PaymentOrderResponse order = paymentOrderService.get(payload.orderId());
        if (clearingOrderMapper.selectCount(
                Wrappers.<ClearingOrder>query().eq("order_id", order.id())) > 0) {
            log.info("[清分消费者] 订单已清分，跳过: orderId={}", order.id());
            return;
        }
        clear(order, payload.paidAt().toLocalDate());
    }

    private void clear(PaymentOrderResponse order, LocalDate settleDate) {
        String channelCode = paymentTransactionService.findChannelCode(order.id());
        if (channelCode == null) {
            channelCode = UNKNOWN_CHANNEL;
            log.warn("[清分消费者] 未找到收款渠道交互记录，渠道成本按 0 计: orderId={}", order.id());
        }
        ClearingFeeRateView rate = resolveRate(order.merchantId());
        Long channelCostBps = UNKNOWN_CHANNEL.equals(channelCode) ? null
                : channelConfigService.getCostRateBps(channelCode);
        FeeCalculator.Split split = feeCalculator.split(order.amount(), rate, channelCostBps);

        ClearingOrder clearing = new ClearingOrder();
        clearing.setOrderId(order.id());
        clearing.setMerchantId(order.merchantId());
        clearing.setPlatformOrderNo(order.platformOrderNo());
        clearing.setPayAmount(order.amount());
        clearing.setMerchantIncome(split.merchantIncome());
        clearing.setPlatformFee(split.platformFee());
        clearing.setChannelCost(split.channelCost());
        clearing.setCurrency(order.currency());
        clearing.setChannelCode(channelCode);
        clearing.setSettleDate(settleDate);
        clearing.setStatus("CLEARED");
        try {
            clearingOrderMapper.insert(clearing);
        } catch (DuplicateKeyException e) {
            // Redis 去重窗口外的重投（并发消费者/重平衡），uk(order_id) 兜底
            log.info("[清分消费者] 清分单已存在，跳过重复落库: orderId={}", order.id());
            return;
        }
        insertDetail(clearing.getId(), ClearingDetail.PARTY_MERCHANT, order.merchantId(), null,
                split.merchantIncome(), "商户应得");
        insertDetail(clearing.getId(), ClearingDetail.PARTY_PLATFORM, 0L, null,
                split.platformFee(), "平台手续费");
        insertDetail(clearing.getId(), ClearingDetail.PARTY_CHANNEL, null, channelCode,
                split.channelCost(), "渠道成本");
        log.info("[清分消费者] 清分完成: orderId={}, payAmount={}, merchant={}, platform={}, channel={}",
                order.id(), order.amount(), split.merchantIncome(), split.platformFee(), split.channelCost());
    }

    /** 商户费率：未配置时回退平台默认费率（S37 配置中心，改配置即生效）。 */
    private ClearingFeeRateView resolveRate(Long merchantId) {
        ClearingFeeRateView rate = rateService.getFeeRateView(merchantId);
        if (rate != null) {
            return rate;
        }
        long defaultBps = configService.getLong(ConfigKeys.MERCHANT_RATE_DEFAULT_BPS);
        log.info("[清分消费者] 商户未配置费率，按平台默认费率计: merchantId={}, rateBps={}", merchantId, defaultBps);
        return new ClearingFeeRateView(merchantId, ClearingFeeRateView.MODE_PLAIN, defaultBps, 0L, List.of());
    }

    private void insertDetail(Long clearingId, String partyType, Long partyId, String channelCode,
                              long amount, String remark) {
        ClearingDetail detail = new ClearingDetail();
        detail.setClearingId(clearingId);
        detail.setPartyType(partyType);
        detail.setPartyId(partyId);
        detail.setChannelCode(channelCode);
        detail.setAmount(amount);
        detail.setRemark(remark);
        clearingDetailMapper.insert(detail);
    }
}
