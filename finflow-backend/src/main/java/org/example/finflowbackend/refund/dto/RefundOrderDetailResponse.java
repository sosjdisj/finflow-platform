package org.example.finflowbackend.refund.dto;

import java.util.List;

/**
 * 退款详情：退款单 + 状态留痕。
 */
public record RefundOrderDetailResponse(RefundOrderResponse refund, List<RefundStateLogResponse> stateLogs) {
}