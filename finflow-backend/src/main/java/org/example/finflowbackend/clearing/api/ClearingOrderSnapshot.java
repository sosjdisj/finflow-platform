package org.example.finflowbackend.clearing.api;

import org.example.finflowbackend.clearing.entity.ClearingOrder;

/**
 * 已清分记录快照（M03 facade）：供结算模块汇总生成结算单，避免跨模块持有 clearing 实体。
 */
public record ClearingOrderSnapshot(Long id, Long orderId, Long merchantId, long payAmount,
                                    long merchantIncome, String currency) {

    public static ClearingOrderSnapshot from(ClearingOrder order) {
        return new ClearingOrderSnapshot(order.getId(), order.getOrderId(), order.getMerchantId(),
                order.getPayAmount(), order.getMerchantIncome(), order.getCurrency());
    }
}
