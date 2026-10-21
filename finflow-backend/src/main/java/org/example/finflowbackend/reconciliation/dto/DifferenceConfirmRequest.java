package org.example.finflowbackend.reconciliation.dto;

/**
 * 差异单确认请求：人工审核后选定处理方式（ADJUST 调账 / SUPPLEMENT 补单）并留审核说明。
 */
public record DifferenceConfirmRequest(String handleType, String handleRemark) {
}
