package org.example.finflowbackend.system.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 用户分配角色请求：全量覆盖（以提交列表为准）。
 */
public record AssignRolesRequest(@NotNull List<Long> roleIds) {
}
