package org.example.finflowbackend.system.service.impl;

import org.example.finflowbackend.audit.AuditRecorder;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.system.SystemErrorCode;
import org.example.finflowbackend.system.dto.AssignPermissionsRequest;
import org.example.finflowbackend.system.dto.RoleCreateRequest;
import org.example.finflowbackend.system.dto.RoleUpdateRequest;
import org.example.finflowbackend.system.entity.SysRole;
import org.example.finflowbackend.system.entity.SysRolePermission;
import org.example.finflowbackend.system.mapper.SysPermissionMapper;
import org.example.finflowbackend.system.mapper.SysRoleMapper;
import org.example.finflowbackend.system.mapper.SysRolePermissionMapper;
import org.example.finflowbackend.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色服务单测：编码唯一、内置角色保护、分配权限的存在性校验与关联清理。
 */
class RoleServiceImplTest {

    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysRolePermissionMapper rolePermissionMapper = mock(SysRolePermissionMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);
    private final RoleServiceImpl service =
            new RoleServiceImpl(roleMapper, rolePermissionMapper, userRoleMapper, permissionMapper,
                    new AuditRecorder(JsonMapper.builder().build()));

    @Test
    void createRejectsDuplicatedCode() {
        when(roleMapper.selectCount(any())).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(new RoleCreateRequest("SUPER_ADMIN", "超管", null)));

        assertEquals(SystemErrorCode.ROLE_CODE_EXISTS, error.getErrorCode());
    }

    @Test
    void updateRejectsBuiltInRole() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "SUPER_ADMIN", 1));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(1L, new RoleUpdateRequest("新名字", null, 1)));

        assertEquals(SystemErrorCode.ROLE_BUILT_IN_PROTECTED, error.getErrorCode());
    }

    @Test
    void deleteRejectsBuiltInRole() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "SUPER_ADMIN", 1));

        BusinessException error = assertThrows(BusinessException.class, () -> service.delete(1L));

        assertEquals(SystemErrorCode.ROLE_BUILT_IN_PROTECTED, error.getErrorCode());
        verify(roleMapper, never()).deleteById(1L);
    }

    @Test
    void deleteCleansUpAllRelations() {
        when(roleMapper.selectById(5L)).thenReturn(role(5L, "OPERATOR", 0));

        service.delete(5L);

        verify(rolePermissionMapper).delete(any());
        verify(userRoleMapper).delete(any());
        verify(roleMapper).deleteById(5L);
    }

    @Test
    void assignRejectsBuiltInRole() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "SUPER_ADMIN", 1));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assignPermissions(1L, new AssignPermissionsRequest(List.of(11L))));

        assertEquals(SystemErrorCode.ROLE_BUILT_IN_PROTECTED, error.getErrorCode());
    }

    @Test
    void assignRejectsUnknownPermission() {
        when(roleMapper.selectById(5L)).thenReturn(role(5L, "OPERATOR", 0));
        when(permissionMapper.selectCount(any())).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assignPermissions(5L, new AssignPermissionsRequest(List.of(11L, 999L))));

        assertEquals(SystemErrorCode.PERMISSION_NOT_FOUND, error.getErrorCode());
        verify(rolePermissionMapper, never()).delete(any());
    }

    @Test
    void assignReplacesPermissionsFully() {
        when(roleMapper.selectById(5L)).thenReturn(role(5L, "OPERATOR", 0));
        when(permissionMapper.selectCount(any())).thenReturn(2L);

        service.assignPermissions(5L, new AssignPermissionsRequest(List.of(11L, 12L)));

        verify(rolePermissionMapper).delete(any());
        verify(rolePermissionMapper, org.mockito.Mockito.times(2)).insert(any(SysRolePermission.class));
    }

    @Test
    void updateRejectsUnknownRole() {
        when(roleMapper.selectById(404L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(404L, new RoleUpdateRequest("名字", null, 1)));

        assertEquals(SystemErrorCode.ROLE_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void createInsertsNonBuiltInEnabledRole() {
        when(roleMapper.selectCount(any())).thenReturn(0L);

        service.create(new RoleCreateRequest("OPERATOR", "运营", "只读运营"));

        ArgumentCaptor<SysRole> captor = ArgumentCaptor.forClass(SysRole.class);
        verify(roleMapper).insert(captor.capture());
        assertEquals("OPERATOR", captor.getValue().getCode());
        assertEquals(0, captor.getValue().getBuiltIn());
        assertEquals(1, captor.getValue().getStatus());
    }

    private SysRole role(Long id, String code, int builtIn) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setCode(code);
        role.setName(code);
        role.setBuiltIn(builtIn);
        role.setStatus(1);
        return role;
    }
}
