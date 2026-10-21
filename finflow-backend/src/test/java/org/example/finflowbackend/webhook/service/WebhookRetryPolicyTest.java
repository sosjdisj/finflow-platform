package org.example.finflowbackend.webhook.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重试策略单测（S32）：指数退避 1/2/4/8/16 分钟、耗尽判定。
 */
class WebhookRetryPolicyTest {

    @Test
    void backoffFollowsExponentialSequence() {
        List<Long> minutes = new ArrayList<>();
        for (int retry = 1; retry <= 5; retry++) {
            minutes.add(WebhookRetryPolicy.backoff(retry).toMinutes());
        }

        // 验收约定：1 / 2 / 4 / 8 / 16 分钟
        assertEquals(List.of(1L, 2L, 4L, 8L, 16L), minutes);
        assertEquals(Duration.ofMinutes(4), WebhookRetryPolicy.backoff(3));
    }

    @Test
    void backoffRejectsInvalidRetryCount() {
        assertThrows(IllegalArgumentException.class, () -> WebhookRetryPolicy.backoff(0));
    }

    @Test
    void exhaustedJudgedByMaxRetries() {
        // 最大重试 5 次：第 5 次失败后耗尽，第 4 次失败后仍可重试
        assertTrue(WebhookRetryPolicy.exhausted(5, 5));
        assertFalse(WebhookRetryPolicy.exhausted(4, 5));
        assertTrue(WebhookRetryPolicy.exhausted(3, 3));
    }
}
