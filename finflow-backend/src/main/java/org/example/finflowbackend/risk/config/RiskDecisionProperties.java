package org.example.finflowbackend.risk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 风控决策调用配置（finflow.risk.decision）：同步决策的超时预算与超时后的降级策略。
 * 超时预算必须小于调用方整单超时预算；M11 拆进程后还须小于 Feign 读超时，否则外层先超时。
 * 经启动类 @ConfigurationPropertiesScan 注册。
 */
@ConfigurationProperties(prefix = "finflow.risk.decision")
public record RiskDecisionProperties(long timeoutMs, RiskTimeoutStrategy onTimeout) {

    public RiskDecisionProperties {
        if (timeoutMs <= 0) {
            timeoutMs = 500;
        }
        if (onTimeout == null) {
            onTimeout = RiskTimeoutStrategy.ALLOW;
        }
    }
}
