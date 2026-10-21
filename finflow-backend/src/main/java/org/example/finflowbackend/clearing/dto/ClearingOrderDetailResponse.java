package org.example.finflowbackend.clearing.dto;

import java.util.List;

/**
 * 清分详情：清分单 + 分账明细。
 */
public record ClearingOrderDetailResponse(ClearingOrderResponse clearing, List<ClearingDetailResponse> details) {
}
