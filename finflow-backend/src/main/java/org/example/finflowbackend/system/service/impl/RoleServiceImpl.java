package org.example.finflowbackend.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.audit.AuditRecorder;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.system.SystemErrorCode;
import org.example.finflowbackend.system.dto.AssignPermissionsRequest;
import org.example.finflowbackend.system.dto.RoleCreateRequest;
import org.example.finflowbackend.system.dto.RolePermissionsResponse;
import org.example.finflowbackend.system.dto.RoleResponse;
import org.example.finflowbackend.system.dto.RoleUpdateRequest;
import org.example.finflowbackend.system.entity.SysPermission;
import org.example.finflowbackend.system.entity.SysRole;
import org.example.finflowbackend.system.entity.SysRolePermission;
import org.example.finflowbackend.system.entity.SysUserRole;
import org.example.finflowbackend.system.mapper.SysPermissionMapper;
import org.example.finflowbackend.system.mapper.SysRoleMapper;
import org.example.finflowbackend.system.mapper.SysRolePermissionMapper;
import org.example.finflowbackend.system.mapper.SysUserRoleMapper;
import org.example.finflowbackend.system.service.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色管理实现。内置角色（builtIn = 1）禁改禁删禁分配；
 * 删除角色时在同一事务内清理其全部关联，不留脏数据。
 */
@Service
public class RoleServiceImpl implements RoleService {

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysPermissionMapper permissionMapper;
    private final AuditRecorder auditRecorder;

    public RoleServiceImpl(SysRoleMapper roleMapper, SysRolePermissionMapper rolePermissionMapper,
                           SysUserRoleMapper userRoleMapper, SysPermissionMapper permissionMapper,
                           AuditRecorder auditRecorder) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionMapper = permissionMapper;
        this.auditRecorder = auditRecorder;
    }

    @Override
    public PageResponse<RoleResponse> page(long page, long size) {
        Page<SysRole> result = roleMapper.selectPage(new Page<>(page, size),
                Wrappers.<SysRole>query().orderByDesc("id"));
        return PageResponse.of(result.convert(RoleResponse::from));
    }

    @Override
    public RoleResponse create(RoleCreateRequest request) {
        Long exists = roleMapper.selectCount(Wrappers.<SysRole>query().eq("code", request.code()));
        if (exists != null && exists > 0) {
            throw new BusinessException(SystemErrorCode.ROLE_CODE_EXISTS);
        }
        SysRole role = new SysRole();
        role.setCode(request.code());
        role.setName(request.name());
        role.setDescription(request.description());
        role.setBuiltIn(0);
        role.setStatus(1);
        roleMapper.insert(role);
        return RoleResponse.from(role);
    }

    @Override
    public RoleResponse update(Long id, RoleUpdateRequest request) {
        SysRole role = requireRole(id);
        requireNotBuiltIn(role);
        SysRole update = new SysRole();
        update.setId(id);
        update.setName(request.name());
        update.setDescription(request.description());
        update.setStatus(request.status());
        roleMapper.updateById(update);
        return RoleResponse.from(requireRole(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysRole role = requireRole(id);
        requireNotBuiltIn(role);
        rolePermissionMapper.delete(Wrappers.<SysRolePermission>query().eq("role_id", id));
        userRoleMapper.delete(Wrappers.<SysUserRole>query().eq("role_id", id));
        roleMapper.deleteById(id);
    }

    @Override
    public RolePermissionsResponse getPermissions(Long roleId) {
        requireRole(roleId);
        List<Long> permissionIds = rolePermissionMapper.selectList(
                        Wrappers.<SysRolePermission>query().eq("role_id", roleId))
                .stream().map(SysRolePermission::getPermissionId).toList();
        return new RolePermissionsResponse(roleId, permissionIds);
    }

    @Override
    @Transactional
    public void assignPermissions(Long roleId, AssignPermissionsRequest request) {
        SysRole role = requireRole(roleId);
        requireNotBuiltIn(role);
        List<Long> permissionIds = request.permissionIds();
        if (!permissionIds.isEmpty()) {
            // 权限点必须全部存在，避免写入悬挂引用
            Long found = permissionMapper.selectCount(
                    Wrappers.<SysPermission>query().in("id", permissionIds));
            if (found == null || found != permissionIds.stream().distinct().count()) {
                throw new BusinessException(SystemErrorCode.PERMISSION_NOT_FOUND);
            }
        }
        List<Long> beforeIds = rolePermissionMapper.selectList(
                        Wrappers.<SysRolePermission>query().eq("role_id", roleId))
                .stream().map(SysRolePermission::getPermissionId).toList();
        rolePermissionMapper.delete(Wrappers.<SysRolePermission>query().eq("role_id", roleId));
        for (Long permissionId : permissionIds) {
            SysRolePermission link = new SysRolePermission();
            link.setRoleId(roleId);
            link.setPermissionId(permissionId);
            rolePermissionMapper.insert(link);
        }
        // 权限变更审计：改前/改后权限 ID 集（S35）
        auditRecorder.submit(beforeIds, List.copyOf(permissionIds));
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(SystemErrorCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    private void requireNotBuiltIn(SysRole role) {
        if (role.getBuiltIn() != null && role.getBuiltIn() == 1) {
            throw new BusinessException(SystemErrorCode.ROLE_BUILT_IN_PROTECTED);
        }
    }
}
