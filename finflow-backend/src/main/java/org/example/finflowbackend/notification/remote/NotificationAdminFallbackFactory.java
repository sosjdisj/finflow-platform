package org.example.finflowbackend.notification.remote;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.notification.NotificationErrorCode;
import org.example.finflowbackend.notification.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 通知管理远程调用降级（M16）：通知服务不可达 / 熔断时快速失败，
 * 统一抛 {@link NotificationErrorCode#NOTIFICATION_SERVICE_UNAVAILABLE}，管理页给出明确提示。
 */
@Component
@ConditionalOnProperty(name = "finflow.notify.mode", havingValue = "remote")
public class NotificationAdminFallbackFactory implements FallbackFactory<NotificationAdminClient> {

    private static final Logger log = LoggerFactory.getLogger(NotificationAdminFallbackFactory.class);

    @Override
    public NotificationAdminClient create(Throwable cause) {
        // 业务错误（404）由 ErrorDecoder 还原后原样抛出，只有基础设施故障才降级为「服务不可用」
        BusinessException error = cause instanceof BusinessException business
                ? business : unavailable();
        if (!(cause instanceof BusinessException)) {
            log.warn("通知服务调用失败，触发降级: {}", cause.toString());
        }
        return new NotificationAdminClient() {
            @Override
            public PageResponse<NotificationResponse> page(long page, long size, Long merchantId,
                                                           String templateCode, String emailStatus) {
                throw error;
            }

            @Override
            public NotificationResponse detail(Long id) {
                throw error;
            }

            @Override
            public long unreadCount() {
                throw error;
            }

            @Override
            public void markRead(Long id) {
                throw error;
            }

            @Override
            public int markAllRead() {
                throw error;
            }
        };
    }

    private static BusinessException unavailable() {
        return new BusinessException(NotificationErrorCode.NOTIFICATION_SERVICE_UNAVAILABLE);
    }
}
