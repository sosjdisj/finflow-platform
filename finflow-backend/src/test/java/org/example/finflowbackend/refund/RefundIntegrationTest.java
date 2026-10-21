package org.example.finflowbackend.refund;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.payment.channel.ChannelRequest;
import org.example.finflowbackend.payment.channel.PaymentChannelRouter;
import org.example.finflowbackend.payment.dto.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.dto.PaymentOrderResponse;
import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.example.finflowbackend.refund.dto.RefundCreateRequest;
import org.example.finflowbackend.refund.dto.RefundOrderResponse;
import org.example.finflowbackend.refund.service.RefundService;
import org.example.finflowbackend.support.FakeChannelStub;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * S21 退款集成验收（依赖本地 MySQL/Redis/Kafka，S39 换 Testcontainers）：
 * ¥100 订单分两次部分退款（30+20）；并发两笔退款总额不超过支付金额；
 * 退款失败释放额度且回退 PAID；成功后平台备付金出账。
 */
class RefundIntegrationTest extends IntegrationTestBase {

    /** 支付金额 ¥100（分）。 */
    private static final long ORDER_AMOUNT = 10000L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentOrderService paymentOrderService;

    @Autowired
    private PaymentChannelRouter channelRouter;

    @Autowired
    private RefundService refundService;

    private Long merchantId;

    @BeforeEach
    void setUp() {
        String suffix = Long.toHexString(System.nanoTime());
        String name = "退款验收商户-" + suffix;
        jdbcTemplate.update("""
                        INSERT INTO merchant (merchant_no, merchant_name, contact_name, contact_phone,
                                              business_license_no, settle_cycle, status)
                        VALUES (?, ?, '测试联系人', '13800000000', ?, 'T1', 'ACTIVE')
                        """,
                "MCH" + suffix, name, "91110000MA01RF" + suffix.substring(0, 5).toUpperCase());
        merchantId = jdbcTemplate.queryForObject(
                "SELECT id FROM merchant WHERE merchant_name = ?", Long.class, name);
    }

    @Test
    void partialRefundsTwiceWithinQuota() throws InterruptedException {
        PaymentOrderResponse order = createPaidOrder("RF-PART-", ORDER_AMOUNT);

        RefundOrderResponse first = refundService.create(
                new RefundCreateRequest(order.id(), 3000L, "部分退款一"), "admin");
        assertEquals("SUCCESS", awaitRefundStatus(first.refundNo()));

        RefundOrderResponse second = refundService.create(
                new RefundCreateRequest(order.id(), 2000L, "部分退款二"), "admin");
        assertEquals("SUCCESS", awaitRefundStatus(second.refundNo()));

        // 剩余可退 = 支付金额 − 已退成功金额；未退满保持 REFUNDING
        assertEquals(5000L, refundedAmount(order.id()));
        assertEquals("REFUNDING", orderStatus(order.id()));
        assertRefundStateLog(first.id(), List.of("PROCESSING", "SUCCESS"));
        assertRefundStateLog(second.id(), List.of("PROCESSING", "SUCCESS"));

        // 超退被拒：剩余 5000，再退 5001 越界
        BusinessException over = assertThrows(BusinessException.class, () -> refundService.create(
                new RefundCreateRequest(order.id(), 5001L, "超额"), "admin"));
        assertEquals(RefundErrorCode.REFUND_AMOUNT_EXCEED, over.getErrorCode());
    }

    @Test
    void concurrentRefundsNeverExceedPaymentAmount() throws Exception {
        PaymentOrderResponse order = createPaidOrder("RF-CONC-", ORDER_AMOUNT);

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return (Object) refundService.create(
                            new RefundCreateRequest(order.id(), 6000L, "并发退款"), "admin");
                } catch (BusinessException e) {
                    return e;
                }
            }));
        }
        start.countDown();
        Object first = futures.getFirst().get(10, TimeUnit.SECONDS);
        Object second = futures.get(1).get(10, TimeUnit.SECONDS);
        pool.shutdown();

        // 恰好一成一败：行锁内校验剩余可退，两笔 6000 不可能都成
        List<Object> results = List.of(first, second);
        long successCount = results.stream().filter(r -> !(r instanceof BusinessException)).count();
        assertEquals(1, successCount);
        RefundOrderResponse success = (RefundOrderResponse) results.stream()
                .filter(r -> r instanceof RefundOrderResponse).findFirst().orElseThrow();

        // 成功回调落定：6000 未退满，订单保持 REFUNDING，额度被成功笔占用
        assertEquals("SUCCESS", awaitRefundStatus(success.refundNo()));
        assertEquals("REFUNDING", orderStatus(order.id()));
        assertEquals(6000L, refundedAmount(order.id()));
    }

    @Test
    void refundFailureReleasesQuotaAndRevertsToPaid() throws InterruptedException {
        PaymentOrderResponse order = createPaidOrder("RF-FAIL-", ORDER_AMOUNT);
        // 支付已成功的订单，把退款回调失败率拉满
        FakeChannelStub.get().setFailRate(1.0);

        RefundOrderResponse refund = refundService.create(
                new RefundCreateRequest(order.id(), 3000L, "会失败的退款"), "admin");
        assertEquals("FAILED", awaitRefundStatus(refund.refundNo()));

        // 额度释放 + 无在途退款 → 支付订单回退 PAID（REFUNDING→PAID）
        assertEquals(0L, refundedAmount(order.id()));
        assertEquals("PAID", awaitOrderStatus(order.id(), "PAID"));
        assertRefundStateLog(refund.id(), List.of("PROCESSING", "FAILED"));

        // 失败后额度完整，可再次退款并成功
        FakeChannelStub.get().setFailRate(0.0);
        RefundOrderResponse retry = refundService.create(
                new RefundCreateRequest(order.id(), 1000L, "失败后重退"), "admin");
        assertEquals("SUCCESS", awaitRefundStatus(retry.refundNo()));
        assertEquals(1000L, refundedAmount(order.id()));
    }

    private PaymentOrderResponse createPaidOrder(String orderNoPrefix, long amount) throws InterruptedException {
        PaymentOrderResponse order = paymentOrderService.create(new PaymentOrderCreateRequest(
                merchantId, orderNoPrefix + System.nanoTime(), amount, null,
                "退款验收商品", null, "ALIPAY", null, null, null, null));
        paymentOrderService.changeStatus(order.id(), "PENDING", "渠道受理", "TEST");
        channelRouter.route("ALIPAY").createPayment(new ChannelRequest(order.id(),
                platformOrderNo(order.id()), null, null, amount, "ALIPAY", "退款验收商品", null));
        assertEquals("PAID", awaitOrderStatus(order.id(), "PAID"));
        return paymentOrderService.get(order.id());
    }

    private String platformOrderNo(Long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT platform_order_no FROM payment_order WHERE id = ?", String.class, orderId);
    }

    private long refundedAmount(Long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT refunded_amount FROM payment_order WHERE id = ?", Long.class, orderId);
    }

    private String orderStatus(Long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM payment_order WHERE id = ?", String.class, orderId);
    }

    private String awaitRefundStatus(String refundNo) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM refund_order WHERE refund_no = ?", String.class, refundNo);
            if ("SUCCESS".equals(status) || "FAILED".equals(status)) {
                return status;
            }
            Thread.sleep(50);
        }
        return jdbcTemplate.queryForObject(
                "SELECT status FROM refund_order WHERE refund_no = ?", String.class, refundNo);
    }

    private String awaitOrderStatus(Long orderId, String target) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            String status = orderStatus(orderId);
            if (target.equals(status)) {
                return status;
            }
            Thread.sleep(50);
        }
        return orderStatus(orderId);
    }

    private void assertRefundStateLog(Long refundId, List<String> expectedToStatuses) {
        List<String> toStatuses = jdbcTemplate.queryForList(
                "SELECT to_status FROM refund_order_state_log WHERE refund_id = ? ORDER BY id",
                String.class, refundId);
        assertEquals(expectedToStatuses, toStatuses);
    }
}
