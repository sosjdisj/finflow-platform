package org.example.finflowbackend.reconciliation.dto;

/**
 * 差异单忽略请求：误报或暂不处理时填写说明（误报原因等）。
 */
public record DifferenceIgnoreRequest(String remark) {
}
