package org.example.finflowbackend.channel.api;

import org.example.finflowbackend.channel.entity.PaymentTransaction;

import java.time.LocalDateTime;

/**
 * 渠道交互留痕快照：跨模块只传快照，不暴露 channel 实体（M06 边界约定）。
 */
public record PaymentTransactionSnapshot(Long id, Long orderId, String channelCode, String txnType,
                                         String channelOrderNo, String requestPayload, String responsePayload,
                                         LocalDateTime createdAt) {

    public static PaymentTransactionSnapshot from(PaymentTransaction txn) {
        return new PaymentTransactionSnapshot(txn.getId(), txn.getOrderId(), txn.getChannelCode(), txn.getTxnType(),
                txn.getChannelOrderNo(), txn.getRequestPayload(), txn.getResponsePayload(), txn.getCreatedAt());
    }
}
