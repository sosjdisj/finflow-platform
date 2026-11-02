package org.example.finflowbackend.webhook.api;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.webhook.dto.WebhookEventDetailResponse;
import org.example.finflowbackend.webhook.dto.WebhookEventResponse;

/**
 * Webhook 事件管理能力（M16 进程边界）：列表 / 详情 / 手动重试。
 *
 * <p>管理端只依赖本接口：{@code finflow.notify.mode=local} 时由 backend 进程内实现，
 * {@code remote} 时由 Feign 调通知服务。内部端点与前台接口共用同一契约。</p>
 */
public interface WebhookAdminApi {

    PageResponse<WebhookEventResponse> page(long page, long size, Long merchantId,
                                            String status, String eventType);

    WebhookEventDetailResponse detail(Long id);

    WebhookEventResponse manualRetry(Long id);
}
