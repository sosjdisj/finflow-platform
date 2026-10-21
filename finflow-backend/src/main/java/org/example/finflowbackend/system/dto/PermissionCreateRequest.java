package org.example.finflowbackend.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新增权限点请求。编码创建后不可修改（方法级权限注解依赖它）。
 */
public record PermissionCreateRequest(
        @NotBlank @Size(max = 128) String code,
        @NotBlank @Size(max = 64) String name,
        @NotNull @Min(1) @Max(2) Integer type,
        @Size(max = 255) String description) {
}
