package org.example.finflowbackend.risk.api;

/** 风控判决三态：放行 / 待人工审核（订单进 REVIEW）/ 拒绝。 */
public enum RiskVerdict {
    ALLOW, REVIEW, REJECT
}
