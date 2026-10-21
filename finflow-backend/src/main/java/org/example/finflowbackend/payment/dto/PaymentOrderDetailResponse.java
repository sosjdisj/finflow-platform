package org.example.finflowbackend.payment.dto;

import java.util.List;

/**
 * 支付订单详情：订单信息 + 状态变更留痕 + 渠道交互记录。
 */
public record PaymentOrderDetailResponse(PaymentOrderResponse order,
                                         List<PaymentOrderStateLogResponse> stateLogs,
                                         List<PaymentTransactionResponse> transactions) {
}
