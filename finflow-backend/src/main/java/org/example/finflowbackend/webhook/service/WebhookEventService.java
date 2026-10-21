package org.example.finflowbackend.webhook.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.webhook.WebhookErrorCode;
import org.example.finflowbackend.webhook.dto.WebhookDeliveryResponse;
import org.example.finflowbackend.webhook.dto.WebhookEventDetailResponse;
import org.example.finflowbackend.webhook.dto.WebhookEventResponse;
import org.example.finflowbackend.webhook.entity.WebhookDelivery;
import org.example.finflowbackend.webhook.entity.WebhookEvent;
import org.example.finflowbackend.webhook.mapper.WebhookDeliveryMapper;
import org.example.finflowbackend.webhook.mapper.WebhookEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Webhook 事件服务：登记（消费入口调用）、查询、手动重试。
 * 登记 uk(event_id) 兜底 Kafka 重复投递（与 Redis 消费去重双保险）。
 */
@Service
public class WebhookEventService {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventService.class);

    private final WebhookEventMapper eventMapper;
    private final WebhookDeliveryMapper deliveryMapper;
    private final WebhookDeliveryService deliveryService;

    public WebhookEventService(WebhookEventMapper eventMapper,
                               WebhookDeliveryMapper deliveryMapper,
                               WebhookDeliveryService deliveryService) {
        this.eventMapper = eventMapper;
        this.deliveryMapper = deliveryMapper;
        this.deliveryService = deliveryService;
    }

    /**
     * 登记待投递事件（payload 为最终投递报文全文）。
     *
     * @return 事件行 ID；重复事件（uk 兜底命中）返回 null
     */
    public Long enqueue(Long merchantId, String eventId, String eventType, String bizOrderNo, String payload) {
        WebhookEvent event = new WebhookEvent();
        event.setMerchantId(merchantId);
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setBizOrderNo(bizOrderNo);
        event.setPayload(payload);
        event.setStatus(WebhookEvent.STATUS_PENDING);
        event.setRetryCount(0);
        try {
            eventMapper.insert(event);
            log.info("[Webhook] 事件已登记: eventId={}, merchantId={}, type={}", eventId, merchantId, eventType);
            return event.getId();
        } catch (DuplicateKeyException e) {
            log.info("[Webhook] 事件已存在，跳过重复登记: eventId={}", eventId);
            return null;
        }
    }

    public PageResponse<WebhookEventResponse> page(long page, long size, Long merchantId,
                                                   String status, String eventType) {
        Page<WebhookEvent> result = eventMapper.selectPage(new Page<>(page, size),
                Wrappers.<WebhookEvent>query()
                        .eq(merchantId != null, "merchant_id", merchantId)
                        .eq(status != null && !status.isBlank(), "status", status)
                        .eq(eventType != null && !eventType.isBlank(), "event_type", eventType)
                        .orderByDesc("id"));
        return PageResponse.of(result.convert(WebhookEventResponse::from));
    }

    public WebhookEventDetailResponse detail(Long id) {
        WebhookEvent event = requireEvent(id);
        List<WebhookDeliveryResponse> deliveries = deliveryMapper.selectList(Wrappers.<WebhookDelivery>query()
                        .eq("event_row_id", id).orderByAsc("attempt_no"))
                .stream().map(WebhookDeliveryResponse::from).toList();
        return new WebhookEventDetailResponse(WebhookEventResponse.from(event), event.getPayload(), deliveries);
    }

    /** 手动重试：重置计数与状态后立即投递一次；成功事件拒绝重试（避免商户重复处理）。 */
    public WebhookEventResponse manualRetry(Long id) {
        WebhookEvent event = requireEvent(id);
        if (WebhookEvent.STATUS_SUCCESS.equals(event.getStatus())) {
            throw new BusinessException(WebhookErrorCode.EVENT_NOT_RETRYABLE);
        }
        deliveryService.deliverAfterReset(event);
        return WebhookEventResponse.from(eventMapper.selectById(id));
    }

    private WebhookEvent requireEvent(Long id) {
        WebhookEvent event = eventMapper.selectById(id);
        if (event == null) {
            throw new BusinessException(WebhookErrorCode.EVENT_NOT_FOUND);
        }
        return event;
    }
}
