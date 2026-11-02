package org.example.finflowbackend.payment.api;

import org.example.finflowbackend.payment.entity.PaymentOrder;

/**
 * 退款额度占用后的订单快照：退款单据此落冗余字段，不再跨模块传递订单实体（M03 边界约定）。
 */
public record PaymentOrderSnapshot(Long id, Long merchantId, String merchantOrderNo, String platformOrderNo,
                                   Long amount, Long refundedAmount, String currency, String subject,
                                   String paymentMethod, String status) {

    public static PaymentOrderSnapshot from(PaymentOrder order) {
        return new PaymentOrderSnapshot(order.getId(), order.getMerchantId(), order.getMerchantOrderNo(),
                order.getPlatformOrderNo(), order.getAmount(), order.getRefundedAmount(), order.getCurrency(),
                order.getSubject(), order.getPaymentMethod(), order.getStatus());
    }
}
