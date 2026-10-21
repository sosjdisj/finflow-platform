package org.example.finflowbackend.payment;

import org.example.finflowbackend.payment.dto.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.dto.PaymentOrderResponse;
import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S13 幂等验收（依赖本地 MySQL 与 Redis，S39 换 Testcontainers）：
 * A. 并发 10 个相同请求只产生 1 个订单（业务幂等：唯一索引 + 冲突返原单）；
 * B. 相同 Idempotency-Key 重复请求返回与首次完全一致的响应（API 幂等：Redis 回放）。
 */
@AutoConfigureMockMvc
class PaymentIdempotencyIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentOrderService paymentOrderService;

    private Long merchantId;

    @BeforeEach
    void insertActiveMerchant() {
        String suffix = Long.toHexString(System.nanoTime());
        String name = "幂等验收商户-" + suffix;
        jdbcTemplate.update("""
                        INSERT INTO merchant (merchant_no, merchant_name, contact_name, contact_phone,
                                              business_license_no, settle_cycle, status)
                        VALUES (?, ?, '测试联系人', '13800000000', ?, 'T1', 'ACTIVE')
                        """,
                "MIT" + suffix, name, "91110000MA01IT" + suffix.substring(0, 5).toUpperCase());
        merchantId = jdbcTemplate.queryForObject(
                "SELECT id FROM merchant WHERE merchant_name = ?", Long.class, name);
    }

    @Test
    void concurrentDuplicateCreatesProduceSingleOrder() throws Exception {
        String merchantOrderNo = "IT-CONC-" + System.nanoTime();
        PaymentOrderCreateRequest request = new PaymentOrderCreateRequest(
                merchantId, merchantOrderNo, 10000L, null, "并发幂等验收", null, "ALIPAY", null, null, null, null);

        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<PaymentOrderResponse>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                futures.add(pool.submit(() -> paymentOrderService.create(request)));
            }
            long firstId = futures.getFirst().get().id();
            for (Future<PaymentOrderResponse> future : futures) {
                assertEquals(firstId, future.get().id());
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1L, orderCount(merchantOrderNo));
    }

    @Test
    void sameIdempotencyKeyReplaysFirstResponse() throws Exception {
        String merchantOrderNo = "IT-KEY-" + System.nanoTime();
        String body = """
                {"merchantId":%d,"merchantOrderNo":"%s","amount":10000,"subject":"幂等验收","paymentMethod":"ALIPAY"}
                """.formatted(merchantId, merchantOrderNo);
        String idempotencyKey = "it-" + UUID.randomUUID();
        RequestPostProcessor auth = createAuthority();

        MvcResult first = mockMvc.perform(post("/api/v1/payments/orders")
                        .with(auth).header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        MvcResult replayed = mockMvc.perform(post("/api/v1/payments/orders")
                        .with(auth).header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        assertEquals(first.getResponse().getContentAsString(), replayed.getResponse().getContentAsString());
        assertEquals(1L, orderCount(merchantOrderNo));
    }

    private RequestPostProcessor createAuthority() {
        return authentication(new UsernamePasswordAuthenticationToken(
                "it-user", "n/a", List.of(new SimpleGrantedAuthority("payment:order:create"))));
    }

    private long orderCount(String merchantOrderNo) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_order WHERE merchant_id = ? AND merchant_order_no = ?",
                Long.class, merchantId, merchantOrderNo);
        return count == null ? 0 : count;
    }
}
