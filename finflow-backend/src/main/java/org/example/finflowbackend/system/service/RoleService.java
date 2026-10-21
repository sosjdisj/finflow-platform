package org.example.finflowbackend.system.service;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.system.dto.AssignPermissionsRequest;
import org.example.finflowbackend.system.dto.RoleCreateRequest;
import org.example.finflowbackend.system.dto.RolePermissionsResponse;
import org.example.finflowbackend.system.dto.RoleResponse;
import org.example.finflowbackend.system.dto.RoleUpdateRequest;

/**
 * 角色管理。内置角色（builtIn = 1）禁止修改、删除与分配权限。
 */
public interface RoleService {

    PageResponse<RoleResponse> page(long page, long size);

    RoleResponse create(RoleCreateRequest request);

    RoleResponse update(Long id, RoleUpdateRequest request);

    void delete(Long id);

    RolePermissionsResponse getPermissions(Long roleId);

    /** 全量覆盖角色的权限关联。 */
    void assignPermissions(Long roleId, AssignPermissionsRequest request);
}
