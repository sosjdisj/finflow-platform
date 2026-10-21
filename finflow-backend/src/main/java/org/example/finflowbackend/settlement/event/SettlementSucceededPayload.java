package org.example.finflowbackend.settlement.event;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 结算完成事件载荷：商户当日应得已入账。
 */
public record SettlementSucceededPayload(String settleNo, Long merchantId, LocalDate settleDate,
                                         Long totalAmount, String currency, LocalDateTime executedAt) {
}
