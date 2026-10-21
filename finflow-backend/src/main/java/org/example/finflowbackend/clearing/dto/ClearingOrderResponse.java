package org.example.finflowbackend.clearing.dto;

import org.example.finflowbackend.clearing.entity.ClearingOrder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 清分单信息。三方向金额恒等式：merchantIncome + platformFee + channelCost = payAmount。
 */
public record ClearingOrderResponse(Long id, Long orderId, Long merchantId, String platformOrderNo,
                                    Long payAmount, Long merchantIncome, Long platformFee, Long channelCost,
                                    String currency, String channelCode, LocalDate settleDate,
                                    String status, LocalDateTime clearedAt) {

    public static ClearingOrderResponse from(ClearingOrder order) {
        return new ClearingOrderResponse(order.getId(), order.getOrderId(), order.getMerchantId(),
                order.getPlatformOrderNo(), order.getPayAmount(), order.getMerchantIncome(),
                order.getPlatformFee(), order.getChannelCost(), order.getCurrency(), order.getChannelCode(),
                order.getSettleDate(), order.getStatus(), order.getClearedAt());
    }
}
