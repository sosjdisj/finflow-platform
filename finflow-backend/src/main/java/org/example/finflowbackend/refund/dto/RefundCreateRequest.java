package org.example.finflowbackend.refund.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 发起退款请求：amount 单位为分，须 ≤ 剩余可退（服务端行锁内校验，防并发超退）。
 */
public record RefundCreateRequest(@NotNull Long orderId, @NotNull @Positive Long amount, String reason) {
}