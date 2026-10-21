package org.example.finflowbackend.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 修改角色请求。角色编码创建后不可修改。
 */
public record RoleUpdateRequest(
        @NotBlank @Size(max = 64) String name,
        @Size(max = 255) String description,
        @NotNull @Min(0) @Max(1) Integer status) {
}
