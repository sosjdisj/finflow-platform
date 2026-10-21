package org.example.finflowbackend.payment.dto;

import org.example.finflowbackend.payment.entity.PaymentTransaction;

import java.time.LocalDateTime;

/**
 * 渠道交互留痕视图（请求/响应报文为 JSON 字符串，前端折叠展示）。
 */
public record PaymentTransactionResponse(Long id, Long orderId, String channelCode, String txnType,
                                         String channelOrderNo, String requestPayload, String responsePayload,
                                         LocalDateTime createdAt) {

    public static PaymentTransactionResponse from(PaymentTransaction txn) {
        return new PaymentTransactionResponse(txn.getId(), txn.getOrderId(), txn.getChannelCode(), txn.getTxnType(),
                txn.getChannelOrderNo(), txn.getRequestPayload(), txn.getResponsePayload(), txn.getCreatedAt());
    }
}
