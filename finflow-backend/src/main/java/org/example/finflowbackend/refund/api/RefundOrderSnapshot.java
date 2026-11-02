package org.example.finflowbackend.refund.api;

import org.example.finflowbackend.refund.dto.RefundOrderResponse;

import java.time.LocalDateTime;

/**
 * 退款单快照（M12）：OpenAPI 与跨模块调用只认该视图，不传 refund 实体与后台 DTO。
 */
public record RefundOrderSnapshot(Long id, String refundNo, Long orderId, Long merchantId,
                                  String platformOrderNo, Long refundAmount, String currency,
                                  String reason, String channelCode, String channelRefundNo,
                                  String status, String failReason,
                                  LocalDateTime refundedAt, LocalDateTime createdAt) {

    public static RefundOrderSnapshot from(RefundOrderResponse response) {
        return new RefundOrderSnapshot(response.id(), response.refundNo(), response.orderId(),
                response.merchantId(), response.platformOrderNo(), response.refundAmount(),
                response.currency(), response.reason(), response.channelCode(), response.channelRefundNo(),
                response.status(), response.failReason(), response.refundedAt(), response.createdAt());
    }
}
