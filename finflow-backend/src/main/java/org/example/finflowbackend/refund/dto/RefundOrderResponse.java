package org.example.finflowbackend.refund.dto;

import org.example.finflowbackend.refund.entity.RefundOrder;

import java.time.LocalDateTime;

/**
 * 退款单信息。
 */
public record RefundOrderResponse(Long id, String refundNo, Long orderId, Long merchantId,
                                  String platformOrderNo, Long refundAmount, String currency,
                                  String reason, String channelCode, String channelRefundNo,
                                  String status, String failReason, String operator,
                                  LocalDateTime refundedAt, LocalDateTime createdAt) {

    public static RefundOrderResponse from(RefundOrder refund) {
        return new RefundOrderResponse(refund.getId(), refund.getRefundNo(), refund.getOrderId(),
                refund.getMerchantId(), refund.getPlatformOrderNo(), refund.getRefundAmount(),
                refund.getCurrency(), refund.getReason(), refund.getChannelCode(),
                refund.getChannelRefundNo(), refund.getStatus(), refund.getFailReason(),
                refund.getOperator(), refund.getRefundedAt(), refund.getCreatedAt());
    }
}