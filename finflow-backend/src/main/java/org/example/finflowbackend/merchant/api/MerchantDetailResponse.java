package org.example.finflowbackend.merchant.api;

import java.util.List;

/**
 * 商户详情：基本信息 + 状态变更留痕（M03 facade 对外视图）。
 */
public record MerchantDetailResponse(MerchantResponse merchant, List<MerchantStatusLogResponse> statusLogs) {
}
