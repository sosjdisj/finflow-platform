package org.example.finflowbackend.settlement.dto;

import org.example.finflowbackend.settlement.entity.SettlementDetail;

/**
 * 结算单明细（逐笔清分引用快照）。
 */
public record SettlementDetailResponse(Long id, Long clearingId, Long orderId,
                                       Long payAmount, Long merchantIncome) {

    public static SettlementDetailResponse from(SettlementDetail detail) {
        return new SettlementDetailResponse(detail.getId(), detail.getClearingId(), detail.getOrderId(),
                detail.getPayAmount(), detail.getMerchantIncome());
    }
}
