package org.example.finflowbackend.payment;

import org.example.finflowbackend.payment.channel.ChannelRequest;
import org.example.finflowbackend.payment.channel.ChannelResult;
import org.example.finflowbackend.payment.channel.PaymentChannel;
import org.example.finflowbackend.payment.channel.PaymentChannelRouter;
import org.example.finflowbackend.payment.dto.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.dto.PaymentOrderResponse;
import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.example.finflowbackend.support.FakeChannelStub;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S14 渠道集成验收（依赖本地 MySQL 与 Redis，S39 换 Testcontainers）：
 * 经路由器调用 Mock 渠道，异步回调后订单分别走完
 * PENDING → PROCESSING → PAID（成功）与 PENDING → FAILED（失败）两条路径，交互与状态留痕齐全。
 */
class PaymentChannelIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentOrderService paymentOrderService;

    @Autowired
    private PaymentChannelRouter channelRouter;

    private Long merchantId;

    @BeforeEach
    void setUp() {
        String suffix = Long.toHexString(System.nanoTime());
        String name = "渠道验收商户-" + suffix;
        jdbcTemplate.update("""
                        INSERT INTO merchant (merchant_no, merchant_name, contact_name, contact_phone,
                                              business_license_no, settle_cycle, status)
                        VALUES (?, ?, '测试联系人', '13800000000', ?, 'T1', 'ACTIVE')
                        """,
                "MCH" + suffix, name, "91110000MA01CH" + suffix.substring(0, 5).toUpperCase());
        merchantId = jdbcTemplate.queryForObject(
                "SELECT id FROM merchant WHERE merchant_name = ?", Long.class, name);
    }

    @Test
    void mockWalletSettlesOrderToPaid() throws InterruptedException {
        FakeChannelStub.get().setFailRate(0.0);
        Long orderId = createPendingOrder("CH-PAID-");

        ChannelResult accepted = channelRouter.route("ALIPAY")
                .createPayment(new ChannelRequest(orderId, platformOrderNo(orderId), null, null,
                        10000L, "ALIPAY", "渠道验收-成功", null));

        assertTrue(accepted.accepted());
        assertEquals("PAID", awaitFinalStatus(orderId));
        assertStateLog(orderId, List.of("PENDING", "PROCESSING", "PAID"));
        assertTransactionRecorded(orderId, accepted.channelOrderNo());
    }

    @Test
    void mockWalletSettlesOrderToFailed() throws InterruptedException {
        FakeChannelStub.get().setFailRate(1.0);
        Long orderId = createPendingOrder("CH-FAIL-");

        ChannelResult accepted = channelRouter.route("WECHAT")
                .createPayment(new ChannelRequest(orderId, platformOrderNo(orderId), null, null,
                        10000L, "WECHAT", "渠道验收-失败", null));

        assertTrue(accepted.accepted());
        assertEquals("FAILED", awaitFinalStatus(orderId));
        assertStateLog(orderId, List.of("PENDING", "FAILED"));
        assertTransactionRecorded(orderId, accepted.channelOrderNo());
    }

    private Long createPendingOrder(String orderNoPrefix) {
        PaymentOrderResponse order = paymentOrderService.create(new PaymentOrderCreateRequest(
                merchantId, orderNoPrefix + System.nanoTime(), 10000L, null,
                "渠道验收商品", null, "ALIPAY", null, null, null, null));
        // 模拟 S15 编排中的「渠道受理」：CREATED → PENDING
        paymentOrderService.changeStatus(order.id(), "PENDING", "渠道受理", "TEST");
        return order.id();
    }

    private String platformOrderNo(Long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT platform_order_no FROM payment_order WHERE id = ?", String.class, orderId);
    }

    /** 轮询等待回调落库（回调在独立线程执行）。 */
    private String awaitFinalStatus(Long orderId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM payment_order WHERE id = ?", String.class, orderId);
            if ("PAID".equals(status) || "FAILED".equals(status)) {
                return status;
            }
            Thread.sleep(50);
        }
        return jdbcTemplate.queryForObject(
                "SELECT status FROM payment_order WHERE id = ?", String.class, orderId);
    }

    private void assertStateLog(Long orderId, List<String> expectedToStatuses) {
        List<String> toStatuses = jdbcTemplate.queryForList(
                "SELECT to_status FROM payment_order_state_log WHERE order_id = ? ORDER BY id",
                String.class, orderId);
        assertEquals(expectedToStatuses, toStatuses);
    }

    private void assertTransactionRecorded(Long orderId, String channelOrderNo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT channel_code, txn_type, channel_order_no FROM payment_transaction WHERE order_id = ?",
                orderId);
        assertEquals(1, rows.size());
        assertEquals("MOCK_WALLET", rows.getFirst().get("channel_code"));
        assertEquals("PAYMENT", rows.getFirst().get("txn_type"));
        assertEquals(channelOrderNo, rows.getFirst().get("channel_order_no"));
    }
}
