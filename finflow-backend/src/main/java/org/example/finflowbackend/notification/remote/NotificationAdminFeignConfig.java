package org.example.finflowbackend.notification.remote;

import feign.codec.ErrorDecoder;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.notification.NotificationErrorCode;
import org.springframework.context.annotation.Bean;

/**
 * 通知管理远程调用的错误码还原（M16）：把通知服务内部端点返回的 404 / 503
 * 还原成 notification 模块的业务错误，避免管理页误报。
 *
 * <p>不加 {@code @Configuration}：本类只通过 {@code @FeignClient(configuration = ...)} 注册到该客户端
 * 的子上下文；若被组件扫描同时拾取会作用到所有 Feign 客户端。</p>
 */
public class NotificationAdminFeignConfig {

    @Bean
    public ErrorDecoder notificationAdminErrorDecoder() {
        ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        return (methodKey, response) -> switch (response.status()) {
            case 404 -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
            case 503 -> new BusinessException(NotificationErrorCode.NOTIFICATION_SERVICE_UNAVAILABLE);
            default -> defaultDecoder.decode(methodKey, response);
        };
    }
}
