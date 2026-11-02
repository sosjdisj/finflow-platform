package org.example.finflowbackend.openapi.web.dto;

import org.example.finflowbackend.refund.api.RefundOrderSnapshot;

import java.time.LocalDateTime;

/**
 * OpenAPI 退款单视图（对外契约）。
 */
public record OpenApiRefundResponse(String refundNo, String platformOrderNo, Long refundAmount,
                                    String currency, String status, String reason,
                                    String failReason, LocalDateTime refundedAt, LocalDateTime createdAt) {

    public static OpenApiRefundResponse from(RefundOrderSnapshot refund) {
        return new OpenApiRefundResponse(refund.refundNo(), refund.platformOrderNo(), refund.refundAmount(),
                refund.currency(), refund.status(), refund.reason(), refund.failReason(),
                refund.refundedAt(), refund.createdAt());
    }
}
