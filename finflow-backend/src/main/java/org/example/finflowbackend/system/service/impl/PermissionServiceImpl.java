package org.example.finflowbackend.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.system.SystemErrorCode;
import org.example.finflowbackend.system.dto.PermissionCreateRequest;
import org.example.finflowbackend.system.dto.PermissionResponse;
import org.example.finflowbackend.system.dto.PermissionUpdateRequest;
import org.example.finflowbackend.system.entity.SysMenu;
import org.example.finflowbackend.system.entity.SysPermission;
import org.example.finflowbackend.system.entity.SysRolePermission;
import org.example.finflowbackend.system.mapper.SysMenuMapper;
import org.example.finflowbackend.system.mapper.SysPermissionMapper;
import org.example.finflowbackend.system.mapper.SysRolePermissionMapper;
import org.example.finflowbackend.system.service.PermissionService;
import org.springframework.stereotype.Service;

/**
 * 权限点管理实现。编码与类型创建后不可修改；被角色或菜单引用时禁止删除，
 * 避免 @PreAuthorize 注解与菜单可见性产生悬挂引用。
 */
@Service
public class PermissionServiceImpl implements PermissionService {

    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysMenuMapper menuMapper;

    public PermissionServiceImpl(SysPermissionMapper permissionMapper, SysRolePermissionMapper rolePermissionMapper,
                                 SysMenuMapper menuMapper) {
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.menuMapper = menuMapper;
    }

    @Override
    public PageResponse<PermissionResponse> page(long page, long size) {
        Page<SysPermission> result = permissionMapper.selectPage(new Page<>(page, size),
                Wrappers.<SysPermission>query().orderByAsc("type").orderByAsc("code"));
        return PageResponse.of(result.convert(PermissionResponse::from));
    }

    @Override
    public PermissionResponse create(PermissionCreateRequest request) {
        if (request.type() != SysPermission.TYPE_MENU && request.type() != SysPermission.TYPE_API) {
            throw new BusinessException(SystemErrorCode.PERMISSION_TYPE_INVALID);
        }
        Long exists = permissionMapper.selectCount(
                Wrappers.<SysPermission>query().eq("code", request.code()));
        if (exists != null && exists > 0) {
            throw new BusinessException(SystemErrorCode.PERMISSION_CODE_EXISTS);
        }
        SysPermission permission = new SysPermission();
        permission.setCode(request.code());
        permission.setName(request.name());
        permission.setType(request.type());
        permission.setDescription(request.description());
        permission.setStatus(1);
        permissionMapper.insert(permission);
        return PermissionResponse.from(permission);
    }

    @Override
    public PermissionResponse update(Long id, PermissionUpdateRequest request) {
        SysPermission permission = requirePermission(id);
        SysPermission update = new SysPermission();
        update.setId(id);
        update.setName(request.name());
        update.setDescription(request.description());
        update.setStatus(request.status());
        permissionMapper.updateById(update);
        return PermissionResponse.from(requirePermission(id));
    }

    @Override
    public void delete(Long id) {
        requirePermission(id);
        Long roleRefs = rolePermissionMapper.selectCount(
                Wrappers.<SysRolePermission>query().eq("permission_id", id));
        Long menuRefs = menuMapper.selectCount(Wrappers.<SysMenu>query().eq("permission_id", id));
        if ((roleRefs != null && roleRefs > 0) || (menuRefs != null && menuRefs > 0)) {
            throw new BusinessException(SystemErrorCode.PERMISSION_IN_USE);
        }
        permissionMapper.deleteById(id);
    }

    private SysPermission requirePermission(Long id) {
        SysPermission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new BusinessException(SystemErrorCode.PERMISSION_NOT_FOUND);
        }
        return permission;
    }
}
