package org.example.finflowbackend.merchant.dto;

import java.util.List;

/**
 * 商户详情：基本信息 + 状态变更留痕。
 */
public record MerchantDetailResponse(MerchantResponse merchant, List<MerchantStatusLogResponse> statusLogs) {
}
