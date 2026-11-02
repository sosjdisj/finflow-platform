package org.example.finflowchannel.config;

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
 * 渠道服务入口流控（M08 / M14）：Sentinel Web 过滤器按请求路径生成资源名，
 * 这里给 4 个内部操作端点加载同一条 QPS 阈值规则。触发流控时 Sentinel 直接返回 429，
 * backend 侧 Feign 收到异常后走降级（快速失败）。
 *
 * <p>M14 起阈值来自配置中心：环境变更事件后按当前值重载，改 Nacos 上的
 * {@code finflow.channel.flow.qps} 无需重启。运行期仍可在 Sentinel 控制台覆盖规则。</p>
 */
@Configuration
public class ChannelFlowSentinelConfig {

    private static final Logger log = LoggerFactory.getLogger(ChannelFlowSentinelConfig.class);

    private static final List<String> INTERNAL_PATHS = List.of(
            "/api/v1/internal/channel/payments",
            "/api/v1/internal/channel/payments/query",
            "/api/v1/internal/channel/refunds",
            "/api/v1/internal/channel/refunds/query");

    private final TunableConfig tunables;

    public ChannelFlowSentinelConfig(TunableConfig tunables) {
        this.tunables = tunables;
        reload();
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChanged() {
        reload();
    }

    private void reload() {
        int qps = tunables.channelFlowQps();
        List<FlowRule> rules = INTERNAL_PATHS.stream()
                .map(path -> new FlowRule(path).setCount(qps))
                .toList();
        FlowRuleManager.loadRules(rules);
        log.info("[渠道] 入口流控规则已加载: paths={}, qps={}", INTERNAL_PATHS.size(), qps);
    }
}
