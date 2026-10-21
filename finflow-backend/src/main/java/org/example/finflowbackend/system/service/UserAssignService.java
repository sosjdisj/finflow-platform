package org.example.finflowbackend.system.service;

import org.example.finflowbackend.system.dto.AssignRolesRequest;
import org.example.finflowbackend.system.dto.UserRolesResponse;

/**
 * 用户角色分配。用户主体属于 auth 模块，经 AuthService 校验存在性，不直接读 sys_user。
 */
public interface UserAssignService {

    UserRolesResponse getUserRoles(Long userId);

    /** 全量覆盖用户的角色关联。 */
    void assignRoles(Long userId, AssignRolesRequest request);
}
