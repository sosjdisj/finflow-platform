package org.example.finflowbackend.channel.remote;

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
 * 渠道服务调用熔断规则（M08 / M14）：Sentinel 通过 Feign 适配层把每次远程调用记为一个资源，
 * 资源名由适配层按「HTTP 方法:服务地址+路径」生成（服务地址为 {@code http://<Feign name>}）。
 * 这里对 4 个内部端点加载慢调用比例熔断规则：慢调用占比超阈值即熔断，
 * 熔断窗口内的调用直接抛 BlockException，由 FallbackFactory 转成快速失败（45002）。
 *
 * <p>M14 起熔断参数来自配置中心：环境变更事件后按当前值重载，无需重启。
 * 资源名与服务名、Feign 路径强相关，改 {@code @FeignClient} 时须同步改这里。</p>
 */
@Configuration
@ConditionalOnProperty(name = "finflow.channel.mode", havingValue = "remote")
public class ChannelSentinelDegradeConfig {

    private static final Logger log = LoggerFactory.getLogger(ChannelSentinelDegradeConfig.class);

    private static final String SERVICE_URL = "http://finflow-channel";

    private static final List<String> PATHS = List.of(
            "/api/v1/internal/channel/payments",
            "/api/v1/internal/channel/payments/query",
            "/api/v1/internal/channel/refunds",
            "/api/v1/internal/channel/refunds/query");

    private final TunableConfig tunables;

    public ChannelSentinelDegradeConfig(TunableConfig tunables) {
        this.tunables = tunables;
        reload();
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChanged() {
        reload();
    }

    private void reload() {
        TunableConfig.Degrade tuning = tunables.channelDegrade();
        List<DegradeRule> rules = PATHS.stream()
                .map(path -> slowCallRule("POST:" + SERVICE_URL + path, tuning))
                .toList();
        DegradeRuleManager.loadRules(rules);
        log.info("[渠道] 熔断规则已加载: resources={}, slowRtMs={}, ratio={}", PATHS.size(),
                tuning.slowRtMs(), tuning.slowRatio());
    }

    private static DegradeRule slowCallRule(String resource, TunableConfig.Degrade tuning) {
        return new DegradeRule(resource)
                .setGrade(RuleConstant.DEGRADE_GRADE_RT)
                .setCount(tuning.slowRtMs())
                .setSlowRatioThreshold(tuning.slowRatio())
                .setMinRequestAmount(tuning.minRequests())
                .setStatIntervalMs(tuning.statIntervalMs())
                .setTimeWindow(tuning.windowSeconds());
    }
}
