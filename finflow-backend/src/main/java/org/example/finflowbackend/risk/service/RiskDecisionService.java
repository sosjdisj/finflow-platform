package org.example.finflowbackend.risk.service;

import org.example.finflowbackend.risk.api.RiskDecisionApi;
import org.example.finflowbackend.risk.api.RiskDecisionRequest;
import org.example.finflowbackend.risk.api.RiskDecisionResponse;
import org.example.finflowbackend.risk.config.RiskDecisionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 风控决策入口（M09）：只做「限时执行 + 超时降级」，规则求值仍由 {@link RiskEngine}。
 * 超时 / 线程池饱和 / 执行异常一律不阻断支付，按 finflow.risk.decision.on-timeout 配置的
 * ALLOW / REVIEW / REJECT 返回；具体原因记 WARN 日志，超时不写 risk_event（没有任何规则命中）。
 *
 * <p>M11 起只在 {@code finflow.risk.mode=local}（含缺省）时装配：backend 进程内模式与风控服务进程
 * 都走本实现；remote 模式下 backend 由 Feign 客户端 {@code RiskDecisionClient} 承担同一契约。</p>
 */
@Service
@ConditionalOnProperty(name = "finflow.risk.mode", havingValue = "local", matchIfMissing = true)
public class RiskDecisionService implements RiskDecisionApi {

    private static final Logger log = LoggerFactory.getLogger(RiskDecisionService.class);

    private final RiskEngine engine;
    private final RiskDecisionProperties properties;
    private final ExecutorService executor;

    public RiskDecisionService(RiskEngine engine, RiskDecisionProperties properties,
                               @Qualifier("riskDecisionExecutor") ExecutorService executor) {
        this.engine = engine;
        this.properties = properties;
        this.executor = executor;
    }

    @Override
    public RiskDecisionResponse decide(RiskDecisionRequest request) {
        try {
            return executor.submit(() -> engine.evaluate(request))
                    .get(properties.timeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return fallback(request, "超时(>" + properties.timeoutMs() + "ms)");
        } catch (RejectedExecutionException e) {
            return fallback(request, "线程池饱和");
        } catch (ExecutionException e) {
            return fallback(request, "执行异常: " + e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback(request, "线程中断");
        }
    }

    /** 降级：按配置策略返回判决，ALLOW 不带原因。 */
    private RiskDecisionResponse fallback(RiskDecisionRequest request, String detail) {
        String reason = "风控决策不可用（" + detail + "），按降级策略 " + properties.onTimeout();
        log.warn("[风控] {}: orderId={}, strategy={}", detail, request.orderId(), properties.onTimeout());
        return switch (properties.onTimeout()) {
            case ALLOW -> RiskDecisionResponse.allow();
            case REVIEW -> RiskDecisionResponse.review(reason);
            case REJECT -> RiskDecisionResponse.reject(reason);
        };
    }
}
