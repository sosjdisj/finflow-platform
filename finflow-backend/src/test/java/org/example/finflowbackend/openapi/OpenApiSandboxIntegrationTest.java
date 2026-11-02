package org.example.finflowbackend.openapi;

import org.example.finflowbackend.common.util.HmacSigner;
import org.example.finflowbackend.merchant.dto.ApiKeyCreateResponse;
import org.example.finflowbackend.merchant.service.ApiKeyService;
import org.example.finflowbackend.openapi.security.InternalAccessGuard;
import org.example.finflowbackend.openapi.security.OpenApiSignature;
import org.example.finflowbackend.payment.api.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.api.PaymentOrderResponse;
import org.example.finflowbackend.payment.service.PaymentOrderService;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M12 完成标志验收（依赖 MySQL 容器与本地 Redis）：
 * 用商户 AK/SK 调通一笔沙箱下单，并覆盖验签失败、时间戳过期、nonce 重放三条拒绝路径。
 * 沙箱形态 = 渠道配置的 MOCK_WALLET（支持 ALIPAY / WECHAT），订单照常落库与后续清结算，不做特判。
 */
@AutoConfigureMockMvc
class OpenApiSandboxIntegrationTest extends IntegrationTestBase {

    private static final String PAYMENTS = "/open/v1/payments";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private PaymentOrderService paymentOrderService;

    @Value("${finflow.internal.token}")
    private String internalToken;

    private Long merchantId;
    private String apiKey;
    private String secret;

    @BeforeEach
    void createActiveMerchantAndCredential() {
        String suffix = Long.toHexString(System.nanoTime());
        String name = "开放平台验收商户-" + suffix;
        jdbcTemplate.update("""
                        INSERT INTO merchant (merchant_no, merchant_name, contact_name, contact_phone,
                                              business_license_no, settle_cycle, status)
                        VALUES (?, ?, '测试联系人', '13800000000', ?, 'T1', 'ACTIVE')
                        """,
                "OIT" + suffix, name, "91110000MA01OA" + suffix.substring(0, 5).toUpperCase());
        merchantId = jdbcTemplate.queryForObject(
                "SELECT id FROM merchant WHERE merchant_name = ?", Long.class, name);

        ApiKeyCreateResponse credential = apiKeyService.create(merchantId);
        apiKey = credential.apiKey();
        secret = credential.secret();
    }

    @Test
    void merchantCanCreateSandboxPaymentWithAkSkSignature() throws Exception {
        String merchantOrderNo = "OA-" + UUID.randomUUID();
        String body = paymentBody(merchantOrderNo, "开放平台沙箱下单");

        mockMvc.perform(signed(post(PAYMENTS), "POST", PAYMENTS, body, System.currentTimeMillis(),
                        UUID.randomUUID().toString())
                        .header("Idempotency-Key", "oa-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.platformOrderNo").exists())
                .andExpect(jsonPath("$.data.merchantOrderNo").value(merchantOrderNo));

        Long orders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_order WHERE merchant_id = ? AND merchant_order_no = ?",
                Long.class, merchantId, merchantOrderNo);
        assertEquals(1L, orders);
    }

    @Test
    void queryAndCloseOwnPayment() throws Exception {
        // 仅建单不受理：渠道桩会在受理后立刻回调成功，PAID 订单不能再关单；
        // 关单路径验证需要一笔停在 CREATED 的订单
        PaymentOrderResponse created = paymentOrderService.create(new PaymentOrderCreateRequest(
                merchantId, "OA-" + UUID.randomUUID(), 10000L, null, "开放平台查单关单", null,
                "ALIPAY", null, null, null, null));
        String platformOrderNo = created.platformOrderNo();
        String queryPath = PAYMENTS + "/" + platformOrderNo;

        mockMvc.perform(signed(get(queryPath), "GET", queryPath, null, System.currentTimeMillis(),
                        UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platformOrderNo").value(platformOrderNo))
                .andExpect(jsonPath("$.data.status").value("CREATED"));

        String closePath = queryPath + "/close";
        mockMvc.perform(signed(post(closePath), "POST", closePath, null, System.currentTimeMillis(),
                        UUID.randomUUID().toString())
                        .header("Idempotency-Key", "oac-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    void badSignatureIsRejected() throws Exception {
        String body = paymentBody("BAD-" + UUID.randomUUID(), "验签失败");
        String nonce = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        mockMvc.perform(post(PAYMENTS)
                        .header(OpenApiSignature.HEADER_API_KEY, apiKey)
                        .header(OpenApiSignature.HEADER_TIMESTAMP, String.valueOf(timestamp))
                        .header(OpenApiSignature.HEADER_NONCE, nonce)
                        .header(OpenApiSignature.HEADER_SIGNATURE, "0".repeat(64))
                        .header("Idempotency-Key", "oabad-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(47002));
    }

    @Test
    void expiredTimestampIsRejected() throws Exception {
        String body = paymentBody("TS-" + UUID.randomUUID(), "时间戳过期");

        mockMvc.perform(signed(post(PAYMENTS), "POST", PAYMENTS, body,
                        System.currentTimeMillis() - 3_600_000L, UUID.randomUUID().toString())
                        .header("Idempotency-Key", "oats-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(47003));
    }

    @Test
    void replayedNonceIsRejected() throws Exception {
        String nonce = UUID.randomUUID().toString();
        String body = paymentBody("NC-" + UUID.randomUUID(), "nonce 重放");
        long timestamp = System.currentTimeMillis();

        mockMvc.perform(signed(post(PAYMENTS), "POST", PAYMENTS, body, timestamp, nonce)
                        .header("Idempotency-Key", "oanc1-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(signed(post(PAYMENTS), "POST", PAYMENTS, body, timestamp, nonce)
                        .header("Idempotency-Key", "oanc2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(47004));
    }

    /** M13：网关已验签的请求不再带签名头，只带内部令牌与商户号，backend 仍能正常建单。 */
    @Test
    void gatewayTrustedRequestDoesNotNeedSignature() throws Exception {
        String body = paymentBody("GTW-" + UUID.randomUUID(), "网关可信路径");

        mockMvc.perform(post(PAYMENTS)
                        .header(InternalAccessGuard.HEADER_TOKEN, internalToken)
                        .header(InternalAccessGuard.HEADER_MERCHANT_ID, merchantId)
                        .header(OpenApiSignature.HEADER_API_KEY, apiKey)
                        .header("Idempotency-Key", "oagw-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.platformOrderNo").exists());
    }

    /** M13：网关取注入档案的内部接口，缺令牌拒绝，带令牌返回 secret 与全部接口配额。 */
    @Test
    void internalProfileRequiresTokenAndReturnsCredentialAndQuotas() throws Exception {
        String path = "/api/v1/internal/openapi/profiles/" + apiKey;

        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(path).header(InternalAccessGuard.HEADER_TOKEN, internalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merchantId").value(merchantId))
                .andExpect(jsonPath("$.data.secret").value(secret))
                .andExpect(jsonPath("$.data.merchantStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.quotas.length()").value(5));
    }

    private String paymentBody(String merchantOrderNo, String subject) {
        return """
                {"merchantOrderNo":"%s","amount":10000,"subject":"%s","paymentMethod":"ALIPAY"}
                """.formatted(merchantOrderNo, subject);
    }

    /** 按 M12 规范串签名并写入四个请求头；method / path 与请求构建器保持一致。 */
    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder builder, String method, String path,
                                                 String body, long timestamp, String nonce) {
        byte[] bodyBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        String canonical = OpenApiSignature.canonical(method, path, null, bodyBytes,
                String.valueOf(timestamp), nonce);
        return builder
                .header(OpenApiSignature.HEADER_API_KEY, apiKey)
                .header(OpenApiSignature.HEADER_TIMESTAMP, String.valueOf(timestamp))
                .header(OpenApiSignature.HEADER_NONCE, nonce)
                .header(OpenApiSignature.HEADER_SIGNATURE, HmacSigner.sign(canonical, secret));
    }
}
