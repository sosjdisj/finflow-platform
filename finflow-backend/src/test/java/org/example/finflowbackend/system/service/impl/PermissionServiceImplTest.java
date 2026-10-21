package org.example.finflowbackend.system.service.impl;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.system.SystemErrorCode;
import org.example.finflowbackend.system.dto.PermissionCreateRequest;
import org.example.finflowbackend.system.entity.SysPermission;
import org.example.finflowbackend.system.mapper.SysMenuMapper;
import org.example.finflowbackend.system.mapper.SysPermissionMapper;
import org.example.finflowbackend.system.mapper.SysRolePermissionMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限点服务单测：编码唯一、类型校验、引用保护（禁止删除被角色或菜单引用的权限）。
 */
class PermissionServiceImplTest {

    private final SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);
    private final SysRolePermissionMapper rolePermissionMapper = mock(SysRolePermissionMapper.class);
    private final SysMenuMapper menuMapper = mock(SysMenuMapper.class);
    private final PermissionServiceImpl service =
            new PermissionServiceImpl(permissionMapper, rolePermissionMapper, menuMapper);

    @Test
    void createRejectsDuplicatedCode() {
        when(permissionMapper.selectCount(any())).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(new PermissionCreateRequest("system:role:list", "角色查询", 2, null)));

        assertEquals(SystemErrorCode.PERMISSION_CODE_EXISTS, error.getErrorCode());
    }

    @Test
    void createRejectsInvalidType() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(new PermissionCreateRequest("bad:type", "坏类型", 9, null)));

        assertEquals(SystemErrorCode.PERMISSION_TYPE_INVALID, error.getErrorCode());
    }

    @Test
    void deleteRejectsPermissionReferencedByRole() {
        when(permissionMapper.selectById(11L)).thenReturn(permission(11L));
        when(rolePermissionMapper.selectCount(any())).thenReturn(2L);
        when(menuMapper.selectCount(any())).thenReturn(0L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.delete(11L));

        assertEquals(SystemErrorCode.PERMISSION_IN_USE, error.getErrorCode());
        verify(permissionMapper, never()).deleteById(11L);
    }

    @Test
    void deleteRejectsPermissionReferencedByMenu() {
        when(permissionMapper.selectById(1L)).thenReturn(permission(1L));
        when(rolePermissionMapper.selectCount(any())).thenReturn(0L);
        when(menuMapper.selectCount(any())).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.delete(1L));

        assertEquals(SystemErrorCode.PERMISSION_IN_USE, error.getErrorCode());
    }

    @Test
    void deleteRemovesUnusedPermission() {
        when(permissionMapper.selectById(31L)).thenReturn(permission(31L));
        when(rolePermissionMapper.selectCount(any())).thenReturn(0L);
        when(menuMapper.selectCount(any())).thenReturn(0L);

        service.delete(31L);

        verify(permissionMapper).deleteById(31L);
    }

    private SysPermission permission(Long id) {
        SysPermission permission = new SysPermission();
        permission.setId(id);
        permission.setCode("system:test:" + id);
        permission.setName("测试权限");
        permission.setType(SysPermission.TYPE_API);
        permission.setStatus(1);
        return permission;
    }
}
