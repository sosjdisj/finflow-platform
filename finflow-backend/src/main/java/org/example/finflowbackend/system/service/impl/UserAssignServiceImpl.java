package org.example.finflowbackend.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.audit.AuditRecorder;
import org.example.finflowbackend.auth.service.AuthService;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.system.SystemErrorCode;
import org.example.finflowbackend.system.dto.AssignRolesRequest;
import org.example.finflowbackend.system.dto.UserRolesResponse;
import org.example.finflowbackend.system.entity.SysRole;
import org.example.finflowbackend.system.entity.SysUserRole;
import org.example.finflowbackend.system.mapper.SysRoleMapper;
import org.example.finflowbackend.system.mapper.SysUserRoleMapper;
import org.example.finflowbackend.system.service.UserAssignService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户角色分配实现。用户主体属于 auth 模块，经 AuthService 校验存在性（铁律 4：不跨模块读表）。
 */
@Service
public class UserAssignServiceImpl implements UserAssignService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final AuthService authService;
    private final AuditRecorder auditRecorder;

    public UserAssignServiceImpl(SysUserRoleMapper userRoleMapper, SysRoleMapper roleMapper,
                                 AuthService authService, AuditRecorder auditRecorder) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.authService = authService;
        this.auditRecorder = auditRecorder;
    }

    @Override
    public UserRolesResponse getUserRoles(Long userId) {
        requireUser(userId);
        List<Long> roleIds = userRoleMapper.selectList(
                        Wrappers.<SysUserRole>query().eq("user_id", userId))
                .stream().map(SysUserRole::getRoleId).toList();
        return new UserRolesResponse(userId, roleIds);
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, AssignRolesRequest request) {
        requireUser(userId);
        List<Long> roleIds = request.roleIds();
        if (!roleIds.isEmpty()) {
            // 角色必须全部存在，避免写入悬挂引用
            Long found = roleMapper.selectCount(Wrappers.<SysRole>query().in("id", roleIds));
            if (found == null || found != roleIds.stream().distinct().count()) {
                throw new BusinessException(SystemErrorCode.ROLE_NOT_FOUND);
            }
        }
        List<Long> beforeIds = userRoleMapper.selectList(
                        Wrappers.<SysUserRole>query().eq("user_id", userId))
                .stream().map(SysUserRole::getRoleId).toList();
        userRoleMapper.delete(Wrappers.<SysUserRole>query().eq("user_id", userId));
        for (Long roleId : roleIds) {
            SysUserRole link = new SysUserRole();
            link.setUserId(userId);
            link.setRoleId(roleId);
            userRoleMapper.insert(link);
        }
        // 用户角色分配审计：改前/改后角色 ID 集（S35）
        auditRecorder.submit(beforeIds, List.copyOf(roleIds));
    }

    /** 用户存在性经 auth 模块服务校验，用户不存在时其内部抛统一 404。 */
    private void requireUser(Long userId) {
        authService.getUserInfo(userId);
    }
}
