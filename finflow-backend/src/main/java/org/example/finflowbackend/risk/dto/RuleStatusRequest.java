package org.example.finflowbackend.risk.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 规则启停请求。
 */
public record RuleStatusRequest(@NotNull Boolean enabled) {
}
