package org.example.finflowbackend.reconciliation.dto;

/**
 * 发起差异单请求：由对账匹配记录发起（仅非 MATCHED 记录）。
 */
public record DifferenceCreateRequest(Long recordId) {
}
