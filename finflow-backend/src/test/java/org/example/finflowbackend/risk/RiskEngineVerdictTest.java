package org.example.finflowbackend.risk;

import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.common.event.DomainEventPublisher;
import org.example.finflowbackend.payment.risk.RiskCheckContext;
import org.example.finflowbackend.risk.entity.RiskEvent;
import org.example.finflowbackend.risk.entity.RiskRule;
import org.example.finflowbackend.risk.evaluate.AmountLimitEvaluator;
import org.example.finflowbackend.risk.evaluate.BlacklistEvaluator;
import org.example.finflowbackend.risk.event.RiskInterceptedPayload;
import org.example.finflowbackend.risk.event.RiskTopics;
import org.example.finflowbackend.risk.mapper.RiskBlacklistMapper;
import org.example.finflowbackend.risk.mapper.RiskEventMapper;
import org.example.finflowbackend.risk.mapper.RiskRuleMapper;
import org.example.finflowbackend.risk.service.RiskEngine;
import org.example.finflowbackend.system.config.ConfigKeys;
import org.example.finflowbackend.system.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 求值引擎单测：阈值三档边界（29 放行 / 30 待审 / 60 拒绝）、黑名单短路、事件留痕内容。
 */
class RiskEngineVerdictTest {

    private final RiskRuleMapper ruleMapper = mock(RiskRuleMapper.class);
    private final RiskEventMapper eventMapper = mock(RiskEventMapper.class);
    private final RiskBlacklistMapper blacklistMapper = mock(RiskBlacklistMapper.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);

    private RiskEngine engine() {
        return new RiskEngine(ruleMapper, eventMapper,
                List.of(new AmountLimitEvaluator(), new BlacklistEvaluator(blacklistMapper)),
                configService, new ObjectMapper(), eventPublisher);
    }

    /** S37：阈值走配置中心，测试沿用 30/60 默认档。 */
    private void stubThresholds(long review, long reject) {
        when(configService.getLong(ConfigKeys.RISK_REVIEW_THRESHOLD)).thenReturn(review);
        when(configService.getLong(ConfigKeys.RISK_REJECT_THRESHOLD)).thenReturn(reject);
    }

    @Test
    void scoreBelowReviewThresholdAllows() {
        stubThresholds(30, 60);
        stubRules(rule("A", 29));
        RiskCheckContext.Decision decision = engine().check(context(1_000_001L));

        assertEquals(RiskCheckContext.Verdict.ALLOW, decision.verdict());
        assertNull(decision.reason());
        assertEvent(29, "ALLOW", 1);
    }

    @Test
    void scoreAtReviewThresholdSendsToReview() {
        stubThresholds(30, 60);
        stubRules(rule("A", 30));
        RiskCheckContext.Decision decision = engine().check(context(1_000_001L));

        assertEquals(RiskCheckContext.Verdict.REVIEW, decision.verdict());
        assertTrue(decision.reason().contains("审核阈值"));
        assertEvent(30, "REVIEW", 1);
    }

    @Test
    void scoreAccumulatesAcrossRulesToReject() {
        stubThresholds(30, 60);
        stubRules(rule("A", 30), rule("B", 30));
        RiskCheckContext.Decision decision = engine().check(context(1_000_001L));

        assertEquals(RiskCheckContext.Verdict.REJECT, decision.verdict());
        assertTrue(decision.reason().contains("拒绝阈值"));
        assertEvent(60, "REJECT", 2);
    }

    /** S37 验收锚点：阈值改配置即生效（30/10 下总分 20 达拒绝档）。 */
    @Test
    void thresholdsFollowConfigChange() {
        stubThresholds(10, 20);
        stubRules(rule("A", 20));
        RiskCheckContext.Decision decision = engine().check(context(1_000_001L));

        assertEquals(RiskCheckContext.Verdict.REJECT, decision.verdict());
    }

    @Test
    void blacklistHitShortCircuitsRemainingRules() {
        stubThresholds(30, 60);
        when(blacklistMapper.selectCount(any())).thenReturn(1L);
        RiskRule blacklist = rule("黑名单", 0);
        blacklist.setRuleType("BLACKLIST");
        blacklist.setParams("{\"dimension\": \"USER\"}");
        stubRules(blacklist, rule("A", 30));
        RiskCheckContext.Decision decision = engine().check(context(1_000_001L));

        // 直接拒绝且短路：拒绝原因带规则名，事件只留黑名单一条命中
        assertEquals(RiskCheckContext.Verdict.REJECT, decision.verdict());
        assertTrue(decision.reason().startsWith("黑名单"));
        ArgumentCaptor<RiskEvent> captor = ArgumentCaptor.forClass(RiskEvent.class);
        verify(eventMapper).insert((RiskEvent) captor.capture());
        assertTrue(captor.getValue().getHitRules().contains("黑名单"));
        assertTrue(!captor.getValue().getHitRules().contains("\"ruleName\":\"A\"")
                && !captor.getValue().getHitRules().contains("\"ruleName\": \"A\""));
    }

    /** S33 通知源：放行不发事件；拦截（REVIEW/REJECT）发布风控拦截事件，负载带判决/风险分/原因。 */
    @Test
    void interceptPublishesEventButAllowDoesNot() {
        stubThresholds(30, 60);
        stubRules(rule("A", 29));
        engine().check(context(1_000_001L));
        verify(eventPublisher, never()).publish(any(), any(), any());

        stubRules(rule("A", 30));
        engine().check(context(1_000_001L));

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(eq(RiskTopics.TOPIC_INTERCEPTED), eq("7"), captor.capture());
        assertEquals(RiskTopics.TYPE_RISK_INTERCEPTED, captor.getValue().eventType());
        RiskInterceptedPayload payload = new ObjectMapper().convertValue(
                captor.getValue().payload(), RiskInterceptedPayload.class);
        assertEquals("REVIEW", payload.verdict());
        assertEquals(30, payload.totalScore());
        assertTrue(payload.reason().contains("审核阈值"));
        assertEquals(1L, payload.merchantId());
    }

    private RiskCheckContext context(long amount) {
        return new RiskCheckContext(7L, 1L, amount, "ALIPAY", "10.0.0.1", null);
    }

    private RiskRule rule(String name, int score) {
        RiskRule rule = new RiskRule();
        rule.setId(1L);
        rule.setRuleName(name);
        rule.setRuleType("AMOUNT_LIMIT");
        rule.setParams("{\"amountLimit\": 1000000}");
        rule.setScore(score);
        rule.setPriority(10);
        rule.setEnabled(true);
        return rule;
    }

    private void stubRules(RiskRule... rules) {
        when(ruleMapper.selectList(any())).thenReturn(List.of(rules));
        when(eventMapper.selectCount(any())).thenReturn(0L);
        when(eventMapper.insert(any(RiskEvent.class))).thenReturn(1);
    }

    private void assertEvent(int expectedScore, String expectedVerdict, int expectedHits) {
        ArgumentCaptor<RiskEvent> captor = ArgumentCaptor.forClass(RiskEvent.class);
        verify(eventMapper, times(1)).insert((RiskEvent) captor.capture());
        RiskEvent event = captor.getValue();
        assertEquals(expectedScore, event.getTotalScore());
        assertEquals(expectedVerdict, event.getVerdict());
        assertEquals(expectedHits, event.getHitRules().split("ruleType").length - 1);
        assertEquals("RE" + java.time.LocalDate.now().toString().replace("-",
                ""), event.getEventNo().substring(0, 10));
    }
}
