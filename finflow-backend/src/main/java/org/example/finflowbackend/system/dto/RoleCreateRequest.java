package org.example.finflowbackend.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增角色请求。编码创建后不可修改，只允许大写字母、数字与下划线。
 */
public record RoleCreateRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "角色编码须为大写字母开头，仅含大写字母、数字与下划线") String code,
        @NotBlank @Size(max = 64) String name,
        @Size(max = 255) String description) {
}
