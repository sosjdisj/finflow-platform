package org.example.fakechannel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 假渠道全部可调旋钮。
 *
 * @param secret           签名密钥（与 FinFlow 后端约定的共享密钥）
 * @param notifyUrl        异步通知地址前缀，最终地址为 {notifyUrl}/{channelCode}/notify
 * @param payDelaySeconds  受理后延迟多少秒发送异步通知（模拟渠道处理耗时）
 * @param failRate         失败概率，[0,1]；Random.nextDouble() &lt; failRate 判为失败
 * @param notifyTimes      每笔通知推送次数（&gt;1 模拟渠道重复通知，用来验证对端幂等）
 * @param notifyGapSeconds 相邻两次通知的间隔秒数
 * @param responseDelayMs  接口人为延迟毫秒数（&gt;0 时阻塞响应，用来测上游超时）
 */
@ConfigurationProperties(prefix = "finflow.fake-channel")
public record FakeChannelProperties(
        String secret,
        String notifyUrl,
        int payDelaySeconds,
        double failRate,
        int notifyTimes,
        int notifyGapSeconds,
        int responseDelayMs) {

    /** 合法值兜底：延迟不为负、失败率归一到 [0,1]、推送次数至少 1。 */
    public FakeChannelProperties {
        if (secret == null || secret.isBlank()) {
            secret = "finflow-fake-channel-secret";
        }
        if (notifyUrl == null || notifyUrl.isBlank()) {
            notifyUrl = "http://localhost:8080/api/v1/channels";
        }
        if (payDelaySeconds < 0) {
            payDelaySeconds = 0;
        }
        if (failRate < 0.0) {
            failRate = 0.0;
        } else if (failRate > 1.0) {
            failRate = 1.0;
        }
        if (notifyTimes < 1) {
            notifyTimes = 1;
        }
        if (notifyGapSeconds < 0) {
            notifyGapSeconds = 0;
        }
        if (responseDelayMs < 0) {
            responseDelayMs = 0;
        }
    }
}
