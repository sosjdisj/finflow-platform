package org.example.finflowbackend.reconciliation.dto;

/**
 * 差异单执行处理请求（处理成功即关闭）。
 * ADJUST 调账：accountId + 带符号金额（分，正调增/负调减）必填，走 ledger 调账入口；
 * SUPPLEMENT 补单：handleResult 必填（人工补录平台订单后的凭证/平台单号）。
 */
public record DifferenceHandleRequest(Long accountId, Long amount, String handleResult) {
}
