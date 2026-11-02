package org.example.finflowbackend.risk.api;

/** 风控判决结果：REVIEW / REJECT 时 reason 为原因说明，ALLOW 时为 null。 */
public record RiskDecisionResponse(RiskVerdict verdict, String reason) {

    public static RiskDecisionResponse allow() {
        return new RiskDecisionResponse(RiskVerdict.ALLOW, null);
    }

    public static RiskDecisionResponse review(String reason) {
        return new RiskDecisionResponse(RiskVerdict.REVIEW, reason);
    }

    public static RiskDecisionResponse reject(String reason) {
        return new RiskDecisionResponse(RiskVerdict.REJECT, reason);
    }
}
