package org.example.finflowbackend.clearing;

import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.clearing.entity.ClearingDetail;
import org.example.finflowbackend.clearing.entity.ClearingOrder;
import org.example.finflowbackend.clearing.event.ClearingEventConsumer;
import org.example.finflowbackend.clearing.mapper.ClearingDetailMapper;
import org.example.finflowbackend.clearing.mapper.ClearingOrderMapper;
import org.example.finflowbackend.payment.channel.ChannelRequest;
import org.example.finflowbackend.payment.channel.PaymentChannelRouter;
import org.example.finflowbackend.payment.dto.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.dto.PaymentOrderResponse;
import org.example.finflowbackend.payment.event.PaymentSucceededPayload;
import org.example.finflowbackend.payment.event.PaymentTopics;
import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * S23 清分集成验收（依赖本地 MySQL/Redis/Kafka，S39 换 Testcontainers）：
 * 真实支付成功 → Kafka PaymentSucceeded → 清分单 + 三行明细落库，三向之和恒等；
 * 同订单重放事件（新 eventId）被 uk 兜底跳过，不重复清分。
 */
class ClearingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentOrderService paymentOrderService;

    @Autowired
    private PaymentChannelRouter channelRouter;

    @Autowired
    private ClearingOrderMapper clearingOrderMapper;

    @Autowired
    private ClearingDetailMapper clearingDetailMapper;

    @Autowired
    private ClearingEventConsumer clearingConsumer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    private Long merchantId;

    @BeforeEach
    void setUp() throws InterruptedException {
        String suffix = Long.toHexString(System.nanoTime());
        String name = "清分验收商户-" + suffix;
        jdbcTemplate.update("""
                        INSERT INTO merchant (merchant_no, merchant_name, contact_name, contact_phone,
                                              business_license_no, settle_cycle, status)
                        VALUES (?, ?, '测试联系人', '13800000000', ?, 'T1', 'ACTIVE')
                        """,
                "MCH" + suffix, name, "91110000MA01CL" + suffix.substring(0, 5).toUpperCase());
        merchantId = jdbcTemplate.queryForObject(
                "SELECT id FROM merchant WHERE merchant_name = ?", Long.class, name);
        // 比例费率 2%（万分比 200）；渠道成本种子为 100bps（V20）
        jdbcTemplate.update("INSERT INTO merchant_rate (merchant_id, rate_bps, fixed_fee) VALUES (?, 200, NULL)",
                merchantId);
        // 等待 Kafka 监听容器完成分区分配：latest 模式下 rebalance 窗口内发布的事件会被丢掉
        for (int i = 0; i < 100; i++) {
            boolean ready = listenerRegistry.getAllListenerContainers().stream()
                    .allMatch(c -> c.isRunning() && c.getAssignedPartitions() != null
                            && !c.getAssignedPartitions().isEmpty());
            if (ready) {
                return;
            }
            Thread.sleep(100);
        }
    }

    @Test
    void paymentSucceededGeneratesClearingWithThreeWayBalance() throws InterruptedException {
        PaymentOrderResponse order = createPaidOrder(10000L);

        ClearingOrder clearing = awaitClearing(order.id());
        assertEquals(10000L, clearing.getPayAmount());
        // ¥100、费率 2%、渠道 1% → 商户 9700 / 平台 200 / 渠道 100，三向之和 == 支付金额
        assertEquals(200L, clearing.getPlatformFee());
        assertEquals(100L, clearing.getChannelCost());
        assertEquals(9700L, clearing.getMerchantIncome());
        assertEquals(clearing.getPayAmount(),
                clearing.getMerchantIncome() + clearing.getPlatformFee() + clearing.getChannelCost());
        assertEquals("CLEARED", clearing.getStatus());
        assertEquals("MOCK_WALLET", clearing.getChannelCode());
        assertEquals(LocalDateTime.now().toLocalDate(), clearing.getSettleDate());

        List<ClearingDetail> details = clearingDetailMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ClearingDetail>()
                        .eq(ClearingDetail::getClearingId, clearing.getId()));
        assertEquals(3, details.size());
        assertEquals(9700L, detailAmount(details, "MERCHANT"));
        assertEquals(200L, detailAmount(details, "PLATFORM"));
        assertEquals(100L, detailAmount(details, "CHANNEL"));
    }

    @Test
    void replayedEventDoesNotDoubleClear() throws InterruptedException {
        PaymentOrderResponse order = createPaidOrder(5000L);
        awaitClearing(order.id());

        // 新 eventId 的重投（绕过 Redis 去重窗口），uk(order_id) 兜底跳过
        DomainEvent replay = new DomainEvent(UUID.randomUUID().toString(),
                PaymentTopics.TYPE_SUCCEEDED, Instant.now(),
                new PaymentSucceededPayload(order.id(), order.platformOrderNo(), order.amount(), "CNY",
                        LocalDateTime.now()));
        clearingConsumer.handle(objectMapper.writeValueAsString(replay));

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clearing_order WHERE order_id = ?", Long.class, order.id());
        assertEquals(1L, count);
    }

    private long detailAmount(List<ClearingDetail> details, String partyType) {
        return details.stream().filter(d -> partyType.equals(d.getPartyType()))
                .findFirst().orElseThrow().getAmount();
    }

    private PaymentOrderResponse createPaidOrder(long amount) throws InterruptedException {
        PaymentOrderResponse order = paymentOrderService.create(new PaymentOrderCreateRequest(
                merchantId, "CL-" + System.nanoTime(), amount, null,
                "清分验收商品", null, "ALIPAY", null, null, null, null));
        paymentOrderService.changeStatus(order.id(), "PENDING", "渠道受理", "TEST");
        channelRouter.route("ALIPAY").createPayment(new ChannelRequest(order.id(),
                order.platformOrderNo(), null, null, amount, "ALIPAY", "清分验收商品", null));
        awaitStatus(order.id(), "PAID");
        return paymentOrderService.get(order.id());
    }

    private ClearingOrder awaitClearing(Long orderId) throws InterruptedException {
        // Kafka 消费组首次 rebalance 可能耗时数秒，等待窗口放宽到 20s
        for (int i = 0; i < 200; i++) {
            ClearingOrder clearing = clearingOrderMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ClearingOrder>()
                            .eq(ClearingOrder::getOrderId, orderId));
            if (clearing != null) {
                return clearing;
            }
            Thread.sleep(50);
        }
        return clearingOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ClearingOrder>()
                        .eq(ClearingOrder::getOrderId, orderId));
    }

    private void awaitStatus(Long orderId, String target) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM payment_order WHERE id = ?", String.class, orderId);
            if (target.equals(status)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("等待订单状态 " + target + " 超时: orderId=" + orderId);
    }
}
