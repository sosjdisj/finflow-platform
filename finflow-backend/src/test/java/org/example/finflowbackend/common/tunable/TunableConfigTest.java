package org.example.finflowbackend.common.tunable;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * M14 配置中心热读：阈值每次从 Environment 取值（Nacos 推送替换属性源后立即生效，无需重启），
 * 非法配置回退默认，拒绝阈值不低于审核阈值。
 */
class TunableConfigTest {

    private final MockEnvironment environment = new MockEnvironment();
    private final TunableConfig tunables = new TunableConfig(environment);

    @Test
    void riskThresholdsFollowEnvironmentChangeWithoutRecreation() {
        environment.setProperty(TunableConfig.RISK_REVIEW, "10");
        environment.setProperty(TunableConfig.RISK_REJECT, "20");
        assertEquals(10L, tunables.riskReviewThreshold());
        assertEquals(20L, tunables.riskRejectThreshold());

        // 模拟 Nacos Config 推送：属性源被替换，同一个实例再次读取即拿到新值
        environment.setProperty(TunableConfig.RISK_REVIEW, "50");
        environment.setProperty(TunableConfig.RISK_REJECT, "80");
        assertEquals(50L, tunables.riskReviewThreshold());
        assertEquals(80L, tunables.riskRejectThreshold());
    }

    @Test
    void missingOrInvalidValuesFallBackToDefaults() {
        assertEquals(30L, tunables.riskReviewThreshold());
        assertEquals(60L, tunables.riskRejectThreshold());
        assertEquals(20, tunables.openApiDefaultQps());
        assertEquals(50_000L, tunables.openApiDefaultDaily());
        assertEquals(200, tunables.riskFlowQps());
        assertEquals(200, tunables.channelFlowQps());

        environment.setProperty(TunableConfig.RISK_REVIEW, "abc");
        environment.setProperty(TunableConfig.RISK_FLOW_QPS, "0");
        environment.setProperty(TunableConfig.CHANNEL_FLOW_QPS, "-5");
        assertEquals(30L, tunables.riskReviewThreshold());
        assertEquals(200, tunables.riskFlowQps());
        assertEquals(200, tunables.channelFlowQps());
    }

    @Test
    void rejectThresholdIsNeverBelowReviewThreshold() {
        environment.setProperty(TunableConfig.RISK_REVIEW, "80");
        environment.setProperty(TunableConfig.RISK_REJECT, "20");

        // 配置写反时仍然可区分两档，避免 REVIEW 档永远不可达
        assertEquals(80L, tunables.riskRejectThreshold());
    }

    @Test
    void degradeParametersReadWithSaneBounds() {
        environment.setProperty("finflow.risk.degrade.slow-ratio", "1.5");
        environment.setProperty("finflow.risk.degrade.stat-interval-ms", "100");

        TunableConfig.Degrade degrade = tunables.riskDegrade();

        assertEquals(300, degrade.slowRtMs());
        // 超出 (0,1] 的比例回退 0.5；统计窗口不得小于 1 秒
        assertEquals(0.5, degrade.slowRatio());
        assertEquals(1_000, degrade.statIntervalMs());
    }
}
