package org.example.finflowrisk.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.example.finflowbackend.common.tunable.TunableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.List;

/**
 * 风控服务入口流控（M11 / M14）：Sentinel Web 过滤器按请求路径生成资源名，这里给决策端点
 * 加载同一条 QPS 阈值规则；触发流控直接返回 429，backend 侧 Feign 收到异常后按 M09 策略降级。
 *
 * <p>M14 起阈值来自配置中心：监听 {@link EnvironmentChangeEvent} 后按当前值重载规则，
 * 改 Nacos 上的 {@code finflow.risk.flow.qps} 无需重启。运行期仍可在 Sentinel 控制台覆盖规则。</p>
 */
@Configuration
public class RiskFlowSentinelConfig {

    private static final Logger log = LoggerFactory.getLogger(RiskFlowSentinelConfig.class);

    private static final String DECISION_PATH = "/api/v1/internal/risk/decisions";

    private final TunableConfig tunables;

    public RiskFlowSentinelConfig(TunableConfig tunables) {
        this.tunables = tunables;
        reload();
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChanged() {
        reload();
    }

    private void reload() {
        int qps = tunables.riskFlowQps();
        List<FlowRule> rules = List.of(new FlowRule(DECISION_PATH).setCount(qps));
        FlowRuleManager.loadRules(rules);
        log.info("[风控] 入口流控规则已加载: path={}, qps={}", DECISION_PATH, qps);
    }
}
