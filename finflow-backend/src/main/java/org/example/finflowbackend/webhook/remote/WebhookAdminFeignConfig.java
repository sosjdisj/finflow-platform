package org.example.finflowbackend.webhook.remote;

import feign.codec.ErrorDecoder;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.webhook.WebhookErrorCode;
import org.springframework.context.annotation.Bean;

/**
 * Webhook 管理远程调用的错误码还原（M16）：通知服务内部端点用 GlobalExceptionHandler 返回真实状态码，
 * 这里把 404 / 409 还原成 webhook 模块的业务错误，避免管理页把「事件不存在」误报成服务不可用。
 *
 * <p>不加 {@code @Configuration}：本类只通过 {@code @FeignClient(configuration = ...)} 注册到该客户端
 * 的子上下文；若被组件扫描同时拾取会作用到所有 Feign 客户端。</p>
 */
public class WebhookAdminFeignConfig {

    @Bean
    public ErrorDecoder webhookAdminErrorDecoder() {
        ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        return (methodKey, response) -> switch (response.status()) {
            case 404 -> new BusinessException(WebhookErrorCode.EVENT_NOT_FOUND);
            case 409 -> new BusinessException(WebhookErrorCode.EVENT_NOT_RETRYABLE);
            case 503 -> new BusinessException(WebhookErrorCode.WEBHOOK_SERVICE_UNAVAILABLE);
            default -> defaultDecoder.decode(methodKey, response);
        };
    }
}
