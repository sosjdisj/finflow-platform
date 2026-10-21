package org.example.finflowbackend.system.service;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.system.dto.PermissionCreateRequest;
import org.example.finflowbackend.system.dto.PermissionResponse;
import org.example.finflowbackend.system.dto.PermissionUpdateRequest;

/**
 * 权限点管理。编码创建后不可修改；被角色或菜单引用的权限禁止删除。
 */
public interface PermissionService {

    PageResponse<PermissionResponse> page(long page, long size);

    PermissionResponse create(PermissionCreateRequest request);

    PermissionResponse update(Long id, PermissionUpdateRequest request);

    void delete(Long id);
}
