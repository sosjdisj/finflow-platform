package org.example.finflowbackend.system.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 角色分配权限请求：全量覆盖（以提交列表为准）。
 */
public record AssignPermissionsRequest(@NotNull List<Long> permissionIds) {
}
