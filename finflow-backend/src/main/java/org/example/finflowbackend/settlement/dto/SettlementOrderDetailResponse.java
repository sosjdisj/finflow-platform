package org.example.finflowbackend.settlement.dto;

import java.util.List;

/**
 * 结算详情：结算单 + 逐笔明细 + 状态留痕。
 */
public record SettlementOrderDetailResponse(SettlementOrderResponse settlement,
                                            List<SettlementDetailResponse> details,
                                            List<SettlementStateLogResponse> stateLogs) {
}
