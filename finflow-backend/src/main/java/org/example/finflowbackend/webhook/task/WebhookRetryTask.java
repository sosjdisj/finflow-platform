package org.example.finflowbackend.webhook.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.common.lock.RedisLockService;
import org.example.finflowbackend.webhook.entity.WebhookEvent;
import org.example.finflowbackend.webhook.mapper.WebhookEventMapper;
import org.example.finflowbackend.webhook.service.WebhookDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Webhook 重试任务（S32）：每分钟扫描到期待重试事件（status=PENDING 且 next_retry_at<=now），
 * 逐笔重投。指数退避间隔由 WebhookRetryPolicy 计算，最大重试次数读配置。
 * 多实例安全由 Redis 锁保证（沿结算任务写法）；单笔失败不中断整批。
 */
@Component
public class WebhookRetryTask {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryTask.class);

    /** 每轮最多处理条数，防积压时单轮过重。 */
    private static final int BATCH_LIMIT = 100;

    private final WebhookEventMapper eventMapper;
    private final WebhookDeliveryService deliveryService;
    private final RedisLockService lockService;

    public WebhookRetryTask(WebhookEventMapper eventMapper,
                            WebhookDeliveryService deliveryService,
                            RedisLockService lockService) {
        this.eventMapper = eventMapper;
        this.deliveryService = deliveryService;
        this.lockService = lockService;
    }

    @Scheduled(fixedDelayString = "${finflow.webhook.scan-fixed-delay-ms:60000}")
    public void retryDue() {
        lockService.tryRun("webhook:retry:lock", Duration.ofSeconds(55), this::doRetry, () -> {
        });
    }

    /** 扫描入口（公开便于测试直调）。 */
    public int doRetry() {
        List<WebhookEvent> due = eventMapper.selectList(Wrappers.<WebhookEvent>query()
                .eq("status", WebhookEvent.STATUS_PENDING)
                .le("next_retry_at", LocalDateTime.now())
                .orderByAsc("next_retry_at")
                .last("LIMIT " + BATCH_LIMIT));
        if (due.isEmpty()) {
            return 0;
        }
        log.info("[Webhook重试] 触发: 待重试 {} 条", due.size());
        int success = 0;
        for (WebhookEvent event : due) {
            try {
                if (deliveryService.deliver(event.getId())) {
                    success++;
                }
            } catch (Exception e) {
                // 单笔异常不中断整批（沿超时关单任务惯例）
                log.error("[Webhook重试] 单笔处理异常: eventRowId={}", event.getId(), e);
            }
        }
        log.info("[Webhook重试] 结束: 共 {} 条, 本轮成功 {}", due.size(), success);
        return success;
    }
}
