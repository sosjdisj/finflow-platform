package org.example.finflowbackend.risk.config;

/** 风控决策超时 / 不可用时的降级策略（finflow.risk.decision.on-timeout）。 */
public enum RiskTimeoutStrategy {
    ALLOW, REVIEW, REJECT
}
