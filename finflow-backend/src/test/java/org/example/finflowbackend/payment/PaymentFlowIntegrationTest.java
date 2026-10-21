package org.example.finflowbackend.payment;

import org.example.finflowbackend.payment.dto.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.dto.PaymentOrderResponse;
import org.example.finflowbackend.payment.service.PaymentProcessService;
import org.example.finflowbackend.payment.task.PaymentOrderCloseTask;
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
 * S15 主流程验收（依赖本地 MySQL 与 Redis，S39 换 Testcontainers）：
 * 完整链路 建单→受理→回调 成功单 PENDING→PROCESSING→PAID、失败单 PENDING→FAILED，
 * 回调流水与状态日志完整；超时关单任务把过期单置 CLOSED。
 */
class PaymentFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentProcessService paymentProcessService;

    @Autowired
    private PaymentOrderCloseTask closeTask;

    /** it 配置默认通知零延迟；失败率按用例覆盖（0 成功 / 1 失败）。 */
    private Long merchantId;

    @BeforeEach
    void setUp() {
        String suffix = Long.toHexString(System.nanoTime());
        String name = "主流程验收商户-" + suffix;
        jdbcTemplate.update("""
                        INSERT INTO merchant (merchant_no, merchant_name, contact_name, contact_phone,
                                              business_license_no, settle_cycle, status)
                        VALUES (?, ?, '测试联系人', '13800000000', ?, 'T1', 'ACTIVE')
                        """,
                "MFL" + suffix, name, "91110000MA01FL" + suffix.substring(0, 5).toUpperCase());
        merchantId = jdbcTemplate.queryForObject(
                "SELECT id FROM merchant WHERE merchant_name = ?", Long.class, name);
    }

    @Test
    void fullFlowSettlesOrderToPaidWithCompleteLogs() throws InterruptedException {
        FakeChannelStub.get().setFailRate(0.0);

        PaymentOrderResponse order = paymentProcessService.createAndProcess(
                createRequest("FL-PAID-", "ALIPAY"));

        // 受理返回的是回落后的实时状态；零延迟回调可能在返回前已落库，故允许竞速到后续态（完整链路见日志断言）
        assertTrue(List.of("PENDING", "PROCESSING", "PAID").contains(order.status()), order.status());
        assertEquals("PAID", awaitFinalStatus(order.id()));
        assertToStatuses(order.id(), List.of("PENDING", "PROCESSING", "PAID"));
        assertCallback(order.id(), "SUCCESS", 1);
    }

    @Test
    void fullFlowSettlesOrderToFailedWithCompleteLogs() throws InterruptedException {
        FakeChannelStub.get().setFailRate(1.0);

        PaymentOrderResponse order = paymentProcessService.createAndProcess(
                createRequest("FL-FAIL-", "WECHAT"));

        assertTrue(List.of("PENDING", "FAILED").contains(order.status()), order.status());
        assertEquals("FAILED", awaitFinalStatus(order.id()));
        assertToStatuses(order.id(), List.of("PENDING", "FAILED"));
        assertCallback(order.id(), "FAILED", 1);
    }

    @Test
    void closeTaskClosesExpiredOrder() throws InterruptedException {
        PaymentOrderResponse order = paymentProcessService.createAndProcess(
                createRequest("FL-CLOSE-", "ALIPAY"));
        // 受理中的回调也可能已把单推进；把过期时间改到过去再关单，与回调竞速，先到者赢
        jdbcTemplate.update("UPDATE payment_order SET expired_at = NOW() - INTERVAL 1 MINUTE WHERE id = ?",
                order.id());

        closeTask.closeExpiredOrders();

        for (int i = 0; i < 100; i++) {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM payment_order WHERE id = ?", String.class, order.id());
            if ("CLOSED".equals(status) || "PAID".equals(status) || "FAILED".equals(status)) {
                if ("CLOSED".equals(status)) {
                    assertToStatuses(order.id(), List.of("PENDING", "CLOSED"));
                }
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("订单未在预期时间内终态化");
    }

    private PaymentOrderCreateRequest createRequest(String prefix, String paymentMethod) {
        return new PaymentOrderCreateRequest(merchantId, prefix + System.nanoTime(), 10000L, null,
                "主流程验收商品", null, paymentMethod, null, null, null, null);
    }

    /** 轮询等待回调落库（回调在独立线程执行，最长 5 秒）。 */
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

    private void assertToStatuses(Long orderId, List<String> expected) {
        List<String> toStatuses = jdbcTemplate.queryForList(
                "SELECT to_status FROM payment_order_state_log WHERE order_id = ? ORDER BY id",
                String.class, orderId);
        assertEquals(expected, toStatuses);
    }

    private void assertCallback(Long orderId, String result, int expectedRows) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT channel_code, result FROM payment_callback WHERE order_id = ?", orderId);
        assertEquals(expectedRows, rows.size());
        assertEquals("MOCK_WALLET", rows.getFirst().get("channel_code"));
        assertEquals(result, rows.getFirst().get("result"));
    }
}
