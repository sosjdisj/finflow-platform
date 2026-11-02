package org.example.finflowbackend.risk.remote;

import org.example.finflowbackend.risk.api.RiskDecisionRequest;
import org.example.finflowbackend.risk.api.RiskDecisionResponse;
import org.example.finflowbackend.risk.config.RiskDecisionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 风控服务降级（M11）：超时 / 熔断 / 不可达时按 M09 已确定的 {@code finflow.risk.decision.on-timeout}
 * 策略返回，不抛异常、不阻断支付主链。与 M08 渠道服务不同，风控是「有默认判决」的能力，
 * 降级必须给支付编排一个明确三态，而不是失败。
 */
@Component
@ConditionalOnProperty(name = "finflow.risk.mode", havingValue = "remote")
public class RiskDecisionFallbackFactory implements FallbackFactory<RiskDecisionClient> {

    private static final Logger log = LoggerFactory.getLogger(RiskDecisionFallbackFactory.class);

    private final RiskDecisionProperties properties;

    public RiskDecisionFallbackFactory(RiskDecisionProperties properties) {
        this.properties = properties;
    }

    @Override
    public RiskDecisionClient create(Throwable cause) {
        return request -> degrade(request, cause);
    }

    private RiskDecisionResponse degrade(RiskDecisionRequest request, Throwable cause) {
        String reason = "风控服务不可用（" + cause.getClass().getSimpleName() + "），按降级策略 "
                + properties.onTimeout();
        log.warn("[风控] 远程决策降级: orderId={}, strategy={}, cause={}",
                request == null ? null : request.orderId(), properties.onTimeout(), cause.toString());
        return switch (properties.onTimeout()) {
            case ALLOW -> RiskDecisionResponse.allow();
            case REVIEW -> RiskDecisionResponse.review(reason);
            case REJECT -> RiskDecisionResponse.reject(reason);
        };
    }
}
