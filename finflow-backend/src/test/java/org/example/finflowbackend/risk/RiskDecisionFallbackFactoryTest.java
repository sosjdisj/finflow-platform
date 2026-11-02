package org.example.finflowbackend.risk;

import org.example.finflowbackend.risk.api.RiskDecisionApi;
import org.example.finflowbackend.risk.api.RiskDecisionRequest;
import org.example.finflowbackend.risk.api.RiskDecisionResponse;
import org.example.finflowbackend.risk.api.RiskVerdict;
import org.example.finflowbackend.risk.config.RiskDecisionProperties;
import org.example.finflowbackend.risk.config.RiskTimeoutStrategy;
import org.example.finflowbackend.risk.remote.RiskDecisionFallbackFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M11 远程风控降级：风控服务超时 / 熔断 / 不可达时，按 M09 的 on-timeout 策略返回三态，
 * 不抛出异常、不阻断支付。
 */
class RiskDecisionFallbackFactoryTest {

    private static final RiskDecisionRequest REQUEST =
            new RiskDecisionRequest(7L, 1L, 1000L, "10.0.0.1", "device-1", "PO2026001");
    private static final RuntimeException CAUSE = new IllegalStateException("connection refused");

    @Test
    void allowStrategyReturnsAllowWithoutReason() {
        RiskDecisionResponse response = degrade(RiskTimeoutStrategy.ALLOW).decide(REQUEST);

        assertEquals(RiskVerdict.ALLOW, response.verdict());
        assertNull(response.reason());
    }

    @Test
    void reviewStrategyReturnsReviewWithReason() {
        RiskDecisionResponse response = degrade(RiskTimeoutStrategy.REVIEW).decide(REQUEST);

        assertEquals(RiskVerdict.REVIEW, response.verdict());
        assertTrue(response.reason().contains("REVIEW"), response.reason());
    }

    @Test
    void rejectStrategyReturnsRejectWithReason() {
        RiskDecisionResponse response = degrade(RiskTimeoutStrategy.REJECT).decide(REQUEST);

        assertEquals(RiskVerdict.REJECT, response.verdict());
        assertTrue(response.reason().contains("REJECT"), response.reason());
    }

    private static RiskDecisionApi degrade(RiskTimeoutStrategy strategy) {
        return new RiskDecisionFallbackFactory(new RiskDecisionProperties(500, strategy)).create(CAUSE);
    }
}
