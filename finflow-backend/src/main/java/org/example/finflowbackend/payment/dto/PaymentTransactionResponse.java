package org.example.finflowbackend.payment.dto;

import org.example.finflowbackend.channel.api.PaymentTransactionSnapshot;

import java.time.LocalDateTime;

/**
 * 渠道交互留痕视图（请求/响应报文为 JSON 字符串，前端折叠展示）。
 */
public record PaymentTransactionResponse(Long id, Long orderId, String channelCode, String txnType,
                                         String channelOrderNo, String requestPayload, String responsePayload,
                                         LocalDateTime createdAt) {

    public static PaymentTransactionResponse from(PaymentTransactionSnapshot txn) {
        return new PaymentTransactionResponse(txn.id(), txn.orderId(), txn.channelCode(), txn.txnType(),
                txn.channelOrderNo(), txn.requestPayload(), txn.responsePayload(), txn.createdAt());
    }
}
