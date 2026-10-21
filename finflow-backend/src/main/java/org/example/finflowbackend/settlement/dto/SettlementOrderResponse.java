package org.example.finflowbackend.settlement.dto;

import org.example.finflowbackend.settlement.entity.SettlementOrder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 结算单信息。
 */
public record SettlementOrderResponse(Long id, String settleNo, Long merchantId, LocalDate settleDate,
                                      Integer orderCount, Long totalAmount, String currency, String status,
                                      LocalDateTime generatedAt, LocalDateTime calculatedAt,
                                      LocalDateTime approvedAt, String approvedBy,
                                      LocalDateTime executedAt, String failReason) {

    public static SettlementOrderResponse from(SettlementOrder order) {
        return new SettlementOrderResponse(order.getId(), order.getSettleNo(), order.getMerchantId(),
                order.getSettleDate(), order.getOrderCount(), order.getTotalAmount(), order.getCurrency(),
                order.getStatus(), order.getGeneratedAt(), order.getCalculatedAt(),
                order.getApprovedAt(), order.getApprovedBy(), order.getExecutedAt(), order.getFailReason());
    }
}
