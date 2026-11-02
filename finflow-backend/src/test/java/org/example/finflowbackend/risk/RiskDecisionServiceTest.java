package org.example.finflowbackend.risk;

import org.example.finflowbackend.risk.api.RiskDecisionRequest;
import org.example.finflowbackend.risk.api.RiskDecisionResponse;
import org.example.finflowbackend.risk.api.RiskVerdict;
import org.example.finflowbackend.risk.config.RiskDecisionConfig;
import org.example.finflowbackend.risk.config.RiskDecisionProperties;
import org.example.finflowbackend.risk.config.RiskTimeoutStrategy;
import org.example.finflowbackend.risk.service.RiskDecisionService;
import org.example.finflowbackend.risk.service.RiskEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 风控决策入口单测（M09）：预算内透传引擎判决；超时 / 求值异常 / 线程池饱和按 on-timeout 策略降级，不阻断支付。
 */
class RiskDecisionServiceTest {

    private final RiskEngine engine = mock(RiskEngine.class);
    private final ExecutorService executor = new RiskDecisionConfig().riskDecisionExecutor();
    private final RiskDecisionRequest request =
            new RiskDecisionRequest(7L, 1L, 10000L, "10.0.0.1", null, "PO2026001");

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private RiskDecisionService service(long timeoutMs, RiskTimeoutStrategy strategy) {
        return new RiskDecisionService(engine, new RiskDecisionProperties(timeoutMs, strategy), executor);
    }

    /** 正常路径：只做限时包装，判决与原因原样透传。 */
    @Test
    void returnsEngineDecisionWithinBudget() {
        when(engine.evaluate(any())).thenReturn(RiskDecisionResponse.review("风险分 30 达到审核阈值 30"));

        RiskDecisionResponse response = service(500, RiskTimeoutStrategy.ALLOW).decide(request);

        assertEquals(RiskVerdict.REVIEW, response.verdict());
        assertTrue(response.reason().contains("审核阈值"));
        verify(engine).evaluate(request);
    }

    @Test
    void timeoutFallsBackToAllow() {
        assertTimeoutFallback(RiskTimeoutStrategy.ALLOW, RiskVerdict.ALLOW, null);
    }

    @Test
    void timeoutFallsBackToReview() {
        assertTimeoutFallback(RiskTimeoutStrategy.REVIEW, RiskVerdict.REVIEW, "超时");
    }

    @Test
    void timeoutFallsBackToReject() {
        assertTimeoutFallback(RiskTimeoutStrategy.REJECT, RiskVerdict.REJECT, "超时");
    }

    /** 规则求值抛异常：引擎故障不得冒泡到支付主链路，按策略降级。 */
    @Test
    void engineFailureFallsBack() {
        when(engine.evaluate(any())).thenThrow(new IllegalStateException("规则库不可用"));

        RiskDecisionResponse response = service(500, RiskTimeoutStrategy.REVIEW).decide(request);

        assertEquals(RiskVerdict.REVIEW, response.verdict());
        assertTrue(response.reason().contains("执行异常"));
    }

    /** 线程池饱和（已关闭）直接拒绝提交，同样按策略降级而非抛到调用方。 */
    @Test
    void saturatedExecutorFallsBack() {
        ExecutorService closed = new RiskDecisionConfig().riskDecisionExecutor();
        closed.shutdown();

        RiskDecisionResponse response =
                new RiskDecisionService(engine, new RiskDecisionProperties(500, RiskTimeoutStrategy.ALLOW), closed)
                        .decide(request);

        assertEquals(RiskVerdict.ALLOW, response.verdict());
        assertNull(response.reason());
    }

    /** 引擎阻塞超过预算：到点即返回降级判决，随后放行阻塞任务让线程收尾。 */
    private void assertTimeoutFallback(RiskTimeoutStrategy strategy, RiskVerdict expected, String reasonPart) {
        CountDownLatch release = new CountDownLatch(1);
        when(engine.evaluate(any())).thenAnswer(invocation -> {
            release.await(5, TimeUnit.SECONDS);
            return RiskDecisionResponse.allow();
        });
        try {
            RiskDecisionResponse response = service(50, strategy).decide(request);
            assertEquals(expected, response.verdict());
            if (reasonPart == null) {
                assertNull(response.reason());
            } else {
                assertTrue(response.reason().contains(reasonPart));
            }
        } finally {
            release.countDown();
        }
    }
}
