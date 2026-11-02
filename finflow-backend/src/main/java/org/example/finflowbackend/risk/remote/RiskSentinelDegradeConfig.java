package org.example.finflowbackend.risk.remote;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import org.example.finflowbackend.common.tunable.TunableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.List;

/**
 * 风控服务调用熔断规则（M11 / M14）：Sentinel 通过 Feign 适配层把每次远程调用记为一个资源，
 * 资源名由适配层按「HTTP 方法:服务地址+路径」生成（服务地址为 {@code http://<Feign name>}）。
 * 这里对决策端点加载慢调用比例熔断规则，熔断窗口内的调用直接抛 BlockException，
 * 由 {@link RiskDecisionFallbackFactory} 按 M09 策略降级。
 *
 * <p>M14 起熔断参数来自配置中心：环境变更事件后按当前值重载，无需重启。
 * 资源名与服务名、Feign 路径强相关，改 {@code @FeignClient} 时须同步改这里。</p>
 */
@Configuration
@ConditionalOnProperty(name = "finflow.risk.mode", havingValue = "remote")
public class RiskSentinelDegradeConfig {

    private static final Logger log = LoggerFactory.getLogger(RiskSentinelDegradeConfig.class);

    private static final String RESOURCE = "POST:http://finflow-risk/api/v1/internal/risk/decisions";

    private final TunableConfig tunables;

    public RiskSentinelDegradeConfig(TunableConfig tunables) {
        this.tunables = tunables;
        reload();
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChanged() {
        reload();
    }

    private void reload() {
        TunableConfig.Degrade tuning = tunables.riskDegrade();
        DegradeRuleManager.loadRules(List.of(new DegradeRule(RESOURCE)
                .setGrade(RuleConstant.DEGRADE_GRADE_RT)
                .setCount(tuning.slowRtMs())
                .setSlowRatioThreshold(tuning.slowRatio())
                .setMinRequestAmount(tuning.minRequests())
                .setStatIntervalMs(tuning.statIntervalMs())
                .setTimeWindow(tuning.windowSeconds())));
        log.info("[风控] 熔断规则已加载: resource={}, slowRtMs={}, ratio={}", RESOURCE, tuning.slowRtMs(),
                tuning.slowRatio());
    }
}
