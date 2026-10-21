package org.example.finflowbackend.system.dto;

import jakarta.validation.constraints.NotBlank;

/** 修改系统配置请求（S37）：仅开放值与描述，key 与类型不可改。 */
public record ConfigUpdateRequest(@NotBlank String configValue, String description) {
}
