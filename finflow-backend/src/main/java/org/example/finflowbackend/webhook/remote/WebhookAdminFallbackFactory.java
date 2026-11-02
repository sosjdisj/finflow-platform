package org.example.finflowbackend.webhook.remote;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.webhook.WebhookErrorCode;
import org.example.finflowbackend.webhook.dto.WebhookEventDetailResponse;
import org.example.finflowbackend.webhook.dto.WebhookEventResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Webhook 管理远程调用降级（M16）：通知服务不可达 / 熔断时快速失败，
 * 统一抛 {@link WebhookErrorCode#WEBHOOK_SERVICE_UNAVAILABLE}，管理页给出明确提示。
 */
@Component
@ConditionalOnProperty(name = "finflow.notify.mode", havingValue = "remote")
public class WebhookAdminFallbackFactory implements FallbackFactory<WebhookAdminClient> {

    private static final Logger log = LoggerFactory.getLogger(WebhookAdminFallbackFactory.class);

    @Override
    public WebhookAdminClient create(Throwable cause) {
        // 业务错误（404 / 409）由 ErrorDecoder 还原后原样抛出，只有基础设施故障才降级为「服务不可用」
        BusinessException error = cause instanceof BusinessException business
                ? business : unavailable();
        if (!(cause instanceof BusinessException)) {
            log.warn("通知服务调用失败，触发降级: {}", cause.toString());
        }
        return new WebhookAdminClient() {
            @Override
            public PageResponse<WebhookEventResponse> page(long page, long size, Long merchantId,
                                                           String status, String eventType) {
                throw error;
            }

            @Override
            public WebhookEventDetailResponse detail(Long id) {
                throw error;
            }

            @Override
            public WebhookEventResponse manualRetry(Long id) {
                throw error;
            }
        };
    }

    private static BusinessException unavailable() {
        return new BusinessException(WebhookErrorCode.WEBHOOK_SERVICE_UNAVAILABLE);
    }
}
