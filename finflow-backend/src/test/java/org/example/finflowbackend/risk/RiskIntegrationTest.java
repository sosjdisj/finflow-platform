package org.example.finflowbackend.risk;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.payment.PaymentErrorCode;
import org.example.finflowbackend.payment.dto.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.dto.PaymentOrderResponse;
import org.example.finflowbackend.payment.service.PaymentProcessService;
import org.example.finflowbackend.risk.entity.RiskBlacklist;
import org.example.finflowbackend.risk.entity.RiskRule;
import org.example.finflowbackend.risk.service.RiskBlacklistService;
import org.example.finflowbackend.risk.service.RiskReviewService;
import org.example.finflowbackend.risk.service.RiskRuleService;
import org.example.finflowbackend.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S30 集成验收（报告：配置「金额 > 10000 加 30 分」大额订单进 REVIEW；黑名单用户被直接拒绝）：
 * 大额 REVIEW → 人工通过回主链路到 PAID（审核闭环）/ 人工拒绝置 FAILED；
 * 黑名单直接拒绝；频次、同 IP 多账户规则；停用规则不生效；规则参数写入校验。
 * 规则/黑名单按名称前缀 IT-RISK 清理，不污染共享 it 库中的其他用例。
 */
class RiskIntegrationTest extends IntegrationTestBase {

    private static final String RULE_PREFIX = "IT-RISK-";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PaymentProcessService paymentProcessService;
    @Autowired
    private RiskRuleService ruleService;
    @Autowired
    private RiskBlacklistService blacklistService;
    @Autowired
    private RiskReviewService reviewService;
    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    private final List<Long> ruleIds = new ArrayList<>();
    private final List<Long> blacklistIds = new ArrayList<>();
    private Long merchantId;

    @BeforeEach
    void setUp() {
        // 清理历史运行残留（含异常中断留下的规则），保证求值器只看到本用例的配置
        jdbcTemplate.update("DELETE FROM risk_rule WHERE rule_name LIKE ?", RULE_PREFIX + "%");
        merchantId = createMerchant();
    }

    @AfterEach
    void tearDown() {
        ruleIds.forEach(id -> jdbcTemplate.update("DELETE FROM risk_rule WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM risk_rule WHERE rule_name LIKE ?", RULE_PREFIX + "%");
        blacklistIds.forEach(id -> jdbcTemplate.update("DELETE FROM risk_blacklist WHERE id = ?", id));
    }

    @Test
    void amountLimitRuleSendsLargeOrderToReviewAndApproveResumesFlow() throws InterruptedException {
        // 报告验收示例：金额 > 10000 元（1000000 分）加 30 分 → 达到审核阈值进 REVIEW
        createRule("大额审核", "AMOUNT_LIMIT", """
                {"amountLimit": 1000000}""", 30, 10);

        PaymentOrderResponse order = paymentProcessService.createAndProcess(
                createRequest("RK-REVIEW-", 1_000_001L, null, null));

        assertEquals("REVIEW", orderStatus(order.id()));
        Map<String, Object> event = riskEvent(order.id());
        assertEquals("REVIEW", event.get("verdict"));
        assertEquals(30, event.get("total_score"));
        assertTrue(String.valueOf(event.get("hit_rules")).contains("大额审核"));

        // 人工通过：回 PENDING 并渠道路由，回调异步落 PAID（审核闭环）
        reviewService.approve(order.id(), "admin", "大额订单人工放行");
        assertEquals("PAID", awaitFinalStatus(order.id()));
        assertEquals(List.of("PENDING", "REVIEW", "PENDING", "PROCESSING", "PAID"), toStatuses(order.id()));

        // 人工拒绝路径：再造一笔大额单，拒绝后 FAILED
        PaymentOrderResponse order2 = paymentProcessService.createAndProcess(
                createRequest("RK-REJECT-", 1_000_002L, null, null));
        reviewService.reject(order2.id(), "admin", "确认可疑交易");
        assertEquals("FAILED", orderStatus(order2.id()));
        assertEquals(List.of("PENDING", "REVIEW", "FAILED"), toStatuses(order2.id()));
    }

    @Test
    void blacklistUserOrderRejectedDirectly() {
        // 黑名单经 BLACKLIST 规则生效（维度由规则指定），名单 + 规则齐备才拦截
        createRule("黑名单", "BLACKLIST", """
                {"dimension": "USER"}""", 0, 1);
        RiskBlacklist entry = new RiskBlacklist();
        entry.setDimension("USER");
        entry.setValue(String.valueOf(merchantId));
        entry.setReason("IT 拉黑验收");
        blacklistIds.add(blacklistService.create(entry, "admin").getId());

        BusinessException error = assertThrows(BusinessException.class,
                () -> paymentProcessService.createAndProcess(createRequest("RK-BL-", 10000L, null, null)));

        assertEquals(PaymentErrorCode.RISK_REJECTED, error.getErrorCode());
        assertEquals("FAILED", orderStatus(latestOrderId()));
        assertEquals("REJECT", riskEvent(latestOrderId()).get("verdict"));
    }

    @Test
    void frequencyRuleHitsSecondOrderInWindow() {
        createRule("频次", "FREQUENCY", """
                {"windowSeconds": 300, "maxCount": 1}""", 30, 10);

        PaymentOrderResponse first = paymentProcessService.createAndProcess(
                createRequest("RK-FREQ-1-", 10000L, null, null));
        assertEquals("PENDING", orderStatus(first.id()));
        assertEquals("ALLOW", riskEvent(first.id()).get("verdict"));

        // 同商户窗口内第二笔：频次命中累 30 分，达到审核阈值
        PaymentOrderResponse second = paymentProcessService.createAndProcess(
                createRequest("RK-FREQ-2-", 10000L, null, null));
        assertEquals("REVIEW", orderStatus(second.id()));
        assertEquals("REVIEW", riskEvent(second.id()).get("verdict"));
    }

    @Test
    void sameIpMultiAccountRuleHitsSecondMerchantOnSameIp() {
        createRule("同IP多账户", "SAME_IP_MULTI_ACCOUNT", """
                {"maxAccounts": 1, "windowSeconds": 300}""", 30, 10);
        Long otherMerchant = createMerchant();
        String ip = "10.20." + (System.nanoTime() % 200) + ".1";
        // 清掉历史运行可能在 TTL 窗口内残留的同 IP 计数键（数据库重建可能复用规则 ID），保证首笔必然未命中
        redisTemplate.delete(org.example.finflowbackend.risk.evaluate.SameIpMultiAccountEvaluator.KEY_PREFIX
                + ruleIds.get(ruleIds.size() - 1) + ":" + ip);

        PaymentOrderResponse first = paymentProcessService.createAndProcess(
                createRequest(merchantId, "RK-IP-1-", 10000L, ip, null));
        assertEquals("PENDING", orderStatus(first.id()));

        // 同 IP 第二个商户：命中累 30 分进 REVIEW
        PaymentOrderResponse second = paymentProcessService.createAndProcess(
                createRequest(otherMerchant, "RK-IP-2-", 10000L, ip, null));
        assertEquals("REVIEW", orderStatus(second.id()));
        assertEquals("REVIEW", riskEvent(second.id()).get("verdict"));
    }

    @Test
    void disabledRuleIsSkipped() {
        Long ruleId = createRule("停用", "AMOUNT_LIMIT", """
                {"amountLimit": 1000000}""", 30, 10);
        ruleService.toggle(ruleId, false);

        PaymentOrderResponse order = paymentProcessService.createAndProcess(
                createRequest("RK-OFF-", 1_000_001L, null, null));

        assertEquals("PENDING", orderStatus(order.id()));
        assertEquals("ALLOW", riskEvent(order.id()).get("verdict"));
    }

    @Test
    void ruleValidationRejectsBadInput() {
        // 参数与类型不符
        BusinessException badParams = assertThrows(BusinessException.class,
                () -> createRule("坏参数", "AMOUNT_LIMIT", """
                        {"windowSeconds": 60}""", 30, 10));
        assertEquals(RiskErrorCode.RULE_PARAMS_INVALID, badParams.getErrorCode());
        // 类型不合法
        BusinessException badType = assertThrows(BusinessException.class,
                () -> createRule("坏类型", "NOT_A_TYPE", "{}", 30, 10));
        assertEquals(RiskErrorCode.RULE_TYPE_INVALID, badType.getErrorCode());
        // 名称重复
        createRule("重名", "AMOUNT_LIMIT", """
                {"amountLimit": 1}""", 0, 10);
        BusinessException dup = assertThrows(BusinessException.class,
                () -> createRule("重名", "AMOUNT_LIMIT", """
                        {"amountLimit": 2}""", 0, 10));
        assertEquals(RiskErrorCode.RULE_NAME_EXISTS, dup.getErrorCode());
    }

    // ---------- 造数与断言工具 ----------

    private Long createMerchant() {
        String suffix = Long.toHexString(System.nanoTime());
        String name = "风控验收商户-" + suffix;
        jdbcTemplate.update("""
                        INSERT INTO merchant (merchant_no, merchant_name, contact_name, contact_phone,
                                              business_license_no, settle_cycle, status)
                        VALUES (?, ?, '测试联系人', '13800000000', ?, 'T1', 'ACTIVE')
                        """,
                "MRK" + suffix, name, "91110000MA01RK" + suffix.substring(0, 5).toUpperCase());
        return jdbcTemplate.queryForObject("SELECT id FROM merchant WHERE merchant_name = ?", Long.class, name);
    }

    private Long createRule(String name, String type, String params, int score, int priority) {
        RiskRule rule = new RiskRule();
        rule.setRuleName(RULE_PREFIX + name);
        rule.setRuleType(type);
        rule.setParams(params);
        rule.setScore(score);
        rule.setPriority(priority);
        rule.setEnabled(true);
        Long id = ruleService.create(rule).getId();
        ruleIds.add(id);
        return id;
    }

    private PaymentOrderCreateRequest createRequest(String prefix, long amount, String clientIp, String deviceId) {
        return createRequest(merchantId, prefix, amount, clientIp, deviceId);
    }

    private PaymentOrderCreateRequest createRequest(Long orderMerchantId, String prefix, long amount,
                                                    String clientIp, String deviceId) {
        return new PaymentOrderCreateRequest(orderMerchantId, prefix + System.nanoTime(), amount, null,
                "风控验收商品", null, "ALIPAY", null, null, clientIp, deviceId);
    }

    private String orderStatus(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM payment_order WHERE id = ?", String.class, orderId);
    }

    private Long latestOrderId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM payment_order WHERE merchant_id = ? ORDER BY id DESC LIMIT 1", Long.class, merchantId);
    }

    private Map<String, Object> riskEvent(Long orderId) {
        return jdbcTemplate.queryForMap(
                "SELECT verdict, total_score, hit_rules FROM risk_event WHERE order_id = ? ORDER BY id DESC LIMIT 1",
                orderId);
    }

    private List<String> toStatuses(Long orderId) {
        return jdbcTemplate.queryForList(
                "SELECT to_status FROM payment_order_state_log WHERE order_id = ? ORDER BY id", String.class, orderId);
    }

    /** 轮询等待回调落库（回调在独立线程执行，最长 5 秒）。 */
    private String awaitFinalStatus(Long orderId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            String status = orderStatus(orderId);
            if ("PAID".equals(status) || "FAILED".equals(status)) {
                return status;
            }
            Thread.sleep(50);
        }
        return orderStatus(orderId);
    }
}
