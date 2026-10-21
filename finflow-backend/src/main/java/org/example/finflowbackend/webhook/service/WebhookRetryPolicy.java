package org.example.finflowbackend.webhook.service;

import java.time.Duration;

/**
 * Webhook 重试策略（S32）：指数退避 1/2/4/8/16 分钟。
 * 首次投递不计入重试；第 n 次重试失败后，下次重试延迟 2^(n-1) 分钟；
 * 累计重试次数达到 maxRetries（默认 5，可配置）即重试耗尽。
 * 状态推进规则集中于此，禁止业务代码散写状态判断。
 */
public final class WebhookRetryPolicy {

    private WebhookRetryPolicy() {
    }

    /** 第 retryCount 次重试失败后到下次重试的等待时长：1/2/4/8/16 分钟（retryCount 1..5）。 */
    public static Duration backoff(int retryCount) {
        if (retryCount < 1) {
            throw new IllegalArgumentException("retryCount 必须 >= 1");
        }
        return Duration.ofMinutes(1L << (retryCount - 1));
    }

    /** 已重试 retryCount 次后是否耗尽（不再自动重试，等待手动重试）。 */
    public static boolean exhausted(int retryCount, int maxRetries) {
        return retryCount >= maxRetries;
    }
}
