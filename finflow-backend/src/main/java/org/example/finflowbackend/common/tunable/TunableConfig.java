package org.example.finflowbackend.common.tunable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * M14 运行时阈值统一读取入口：每次访问都从 {@link Environment} 取值。
 *
 * <p>Nacos Config 推送后环境属性源会被替换，所以这里不需要缓存、也不需要重启；
 * 之所以不用 {@code @ConfigurationProperties} + {@code @RefreshScope}，是因为项目里
 * 配置类都是不可变 record，而刷新作用域需要可重建的代理目标，集中读取更简单可控。</p>
 *
 * <p>环境变量 / 本地 yaml 是缺省来源，Nacos 的 finflow-common.yaml 优先级更高。
 * 非法值（非数字、非正数）回退默认并记 WARN，避免坏配置把业务打挂。</p>
 *
 * <p>独立放在 common.tunable 而不是 common.config：风控 / 渠道进程只按需扫描本包，
 * 不会顺带装配 WebMvcConfig 这类后台单体专属配置。</p>
 */
@Component
public class TunableConfig {

    private static final Logger log = LoggerFactory.getLogger(TunableConfig.class);

    /** 风控人工审核阈值（风险总分）。 */
    public static final String RISK_REVIEW = "finflow.risk.thresholds.review";
    /** 风控直接拒绝阈值（风险总分）。 */
    public static final String RISK_REJECT = "finflow.risk.thresholds.reject";
    /** OpenAPI 平台默认 QPS。 */
    public static final String OPENAPI_DEFAULT_QPS = "finflow.openapi.default-qps-limit";
    /** OpenAPI 平台默认日配额。 */
    public static final String OPENAPI_DEFAULT_DAILY = "finflow.openapi.default-daily-limit";
    /** 风控服务入口 QPS。 */
    public static final String RISK_FLOW_QPS = "finflow.risk.flow.qps";
    /** 渠道服务入口 QPS。 */
    public static final String CHANNEL_FLOW_QPS = "finflow.channel.flow.qps";
    /** M16 Webhook 投递最大重试次数。 */
    public static final String WEBHOOK_MAX_RETRIES = "finflow.webhook.max-retries";

    private static final String RISK_DEGRADE_PREFIX = "finflow.risk.degrade.";
    private static final String CHANNEL_DEGRADE_PREFIX = "finflow.channel.degrade.";

    private final Environment environment;

    public TunableConfig(Environment environment) {
        this.environment = environment;
    }

    public long riskReviewThreshold() {
        return positiveLong(RISK_REVIEW, 30);
    }

    /** 拒绝阈值不得低于审核阈值，避免配置写反导致 REVIEW 档永远不可达。 */
    public long riskRejectThreshold() {
        return Math.max(positiveLong(RISK_REJECT, 60), riskReviewThreshold());
    }

    public int openApiDefaultQps() {
        return positiveInt(OPENAPI_DEFAULT_QPS, 20);
    }

    public long openApiDefaultDaily() {
        return positiveLong(OPENAPI_DEFAULT_DAILY, 50_000);
    }

    public int riskFlowQps() {
        return positiveInt(RISK_FLOW_QPS, 200);
    }

    public int channelFlowQps() {
        return positiveInt(CHANNEL_FLOW_QPS, 200);
    }

    /** Webhook 投递最大重试次数（M16 从 system_config 迁入 Nacos，避免后台与配置中心两个事实源）。 */
    public int webhookMaxRetries() {
        return positiveInt(WEBHOOK_MAX_RETRIES, 5);
    }

    public Degrade riskDegrade() {
        return degrade(RISK_DEGRADE_PREFIX, 300);
    }

    public Degrade channelDegrade() {
        return degrade(CHANNEL_DEGRADE_PREFIX, 1_000);
    }

    /** 慢调用比例熔断参数（字段语义与 Sentinel DegradeRule 一致）。 */
    public record Degrade(int slowRtMs, double slowRatio, int minRequests, int statIntervalMs, int windowSeconds) {
    }

    private Degrade degrade(String prefix, int defaultSlowRtMs) {
        int slowRtMs = positiveInt(prefix + "slow-rt-ms", defaultSlowRtMs);
        double slowRatio = ratio(prefix + "slow-ratio", 0.5);
        int minRequests = positiveInt(prefix + "min-requests", 5);
        // Sentinel 慢调用比例策略要求统计窗口不小于 1 秒
        int statIntervalMs = Math.max(positiveInt(prefix + "stat-interval-ms", 1_000), 1_000);
        int windowSeconds = positiveInt(prefix + "window-seconds", 10);
        return new Degrade(slowRtMs, slowRatio, minRequests, statIntervalMs, windowSeconds);
    }

    private long positiveLong(String key, long fallback) {
        Long value = number(key, Long.class);
        if (value == null || value <= 0) {
            return fallback;
        }
        return value;
    }

    private int positiveInt(String key, int fallback) {
        Integer value = number(key, Integer.class);
        if (value == null || value <= 0) {
            return fallback;
        }
        return value;
    }

    private double ratio(String key, double fallback) {
        Double value = number(key, Double.class);
        if (value == null || value <= 0 || value > 1) {
            return fallback;
        }
        return value;
    }

    private <T extends Number> T number(String key, Class<T> type) {
        try {
            return environment.getProperty(key, type);
        } catch (RuntimeException e) {
            log.warn("[配置中心] 阈值解析失败，回退默认值: key={}, value={}", key,
                    environment.getProperty(key));
            return null;
        }
    }
}
