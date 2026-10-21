package org.example.finflowbackend.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增菜单请求。
 */
public record MenuCreateRequest(
        @NotNull Long parentId,
        @NotBlank @Size(max = 64) String title,
        @Size(max = 128) @Pattern(regexp = "^$|^/[^\\s]*$", message = "路径须以 / 开头") String path,
        @Size(max = 64) String icon,
        @NotNull @Min(1) @Max(2) Integer menuType,
        @NotNull @Min(0) Integer sortNo,
        Long permissionId,
        @NotNull @Min(0) @Max(1) Integer visible) {
}
