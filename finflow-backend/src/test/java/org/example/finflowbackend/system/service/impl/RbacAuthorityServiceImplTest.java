package org.example.finflowbackend.system.service.impl;

import org.example.finflowbackend.system.dto.MenuTreeNode;
import org.example.finflowbackend.system.entity.SysPermission;
import org.example.finflowbackend.system.mapper.SysPermissionMapper;
import org.example.finflowbackend.system.mapper.SysRoleMapper;
import org.example.finflowbackend.system.service.MenuService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 授权查询单测：核心约定——内置超管统一返回全量启用权限，普通用户走角色联查；
 * 菜单树按权限点过滤，空目录剔除。
 */
class RbacAuthorityServiceImplTest {

    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);
    private final MenuService menuService = mock(MenuService.class);
    private final RbacAuthorityServiceImpl service =
            new RbacAuthorityServiceImpl(roleMapper, permissionMapper, menuService);

    @Test
    void superAdminGetsAllEnabledPermissions() {
        when(roleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("SUPER_ADMIN"));
        when(permissionMapper.selectAllEnabledCodes())
                .thenReturn(List.of("system:role:list", "system:user:manage"));

        Set<String> permissions = service.loadPermissionCodes(1L);

        assertEquals(Set.of("system:role:list", "system:user:manage"), permissions);
        verify(permissionMapper, never()).selectPermissionCodesByUserId(any());
    }

    @Test
    void normalUserGetsPermissionsViaRoles() {
        when(roleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("OPERATOR"));
        when(permissionMapper.selectPermissionCodesByUserId(2L)).thenReturn(List.of("system:role:list"));

        Set<String> permissions = service.loadPermissionCodes(2L);

        assertEquals(Set.of("system:role:list"), permissions);
        verify(permissionMapper, never()).selectAllEnabledCodes();
    }

    @Test
    void userWithoutRolesHasNoPermissions() {
        when(roleMapper.selectRoleCodesByUserId(3L)).thenReturn(List.of());

        assertTrue(service.loadPermissionCodes(3L).isEmpty());
        assertTrue(service.loadRoleCodes(3L).isEmpty());
    }

    @Test
    void nullQueryResultsAreTreatedAsEmpty() {
        when(roleMapper.selectRoleCodesByUserId(4L)).thenReturn(null);

        assertTrue(service.loadPermissionCodes(4L).isEmpty());
        assertTrue(service.loadRoleCodes(4L).isEmpty());
    }

    @Test
    void menuTreeKeepsOnlyPagesWhosePermissionIsOwned() {
        when(roleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("OPERATOR"));
        when(permissionMapper.selectPermissionCodesByUserId(2L)).thenReturn(List.of("system:user:manage"));
        when(permissionMapper.selectList(any())).thenReturn(List.of(
                permission(1L, "system:user:manage"), permission(2L, "system:role:list")));
        when(menuService.tree()).thenReturn(List.of(
                directory(List.of(page(10L, 1L), page(11L, 2L)))));

        List<MenuTreeNode> tree = service.loadMenuTree(2L);

        assertEquals(1, tree.size());
        assertEquals(1, tree.getFirst().children().size());
        assertEquals(10L, tree.getFirst().children().getFirst().id());
    }

    @Test
    void menuTreeDropsDirectoriesWithoutVisibleChildren() {
        when(roleMapper.selectRoleCodesByUserId(2L)).thenReturn(List.of("OPERATOR"));
        when(permissionMapper.selectPermissionCodesByUserId(2L)).thenReturn(List.of());
        when(permissionMapper.selectList(any())).thenReturn(List.of(permission(1L, "system:user:manage")));
        when(menuService.tree()).thenReturn(List.of(
                directory(List.of(page(10L, 1L)))));

        assertTrue(service.loadMenuTree(2L).isEmpty());
    }

    @Test
    void superAdminSeesAllMenus() {
        when(roleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("SUPER_ADMIN"));
        when(permissionMapper.selectAllEnabledCodes())
                .thenReturn(List.of("system:user:manage", "system:role:list"));
        when(permissionMapper.selectList(any())).thenReturn(List.of(
                permission(1L, "system:user:manage"), permission(2L, "system:role:list")));
        when(menuService.tree()).thenReturn(List.of(
                directory(List.of(page(10L, 1L), page(11L, 2L)))));

        List<MenuTreeNode> tree = service.loadMenuTree(1L);

        assertEquals(1, tree.size());
        assertEquals(2, tree.getFirst().children().size());
    }

    private static SysPermission permission(Long id, String code) {
        SysPermission entity = new SysPermission();
        entity.setId(id);
        entity.setCode(code);
        return entity;
    }

    private static MenuTreeNode page(Long id, Long permissionId) {
        return new MenuTreeNode(id, 1L, "页面", "/system/page", null, 2, 1, permissionId, 1, List.of());
    }

    private static MenuTreeNode directory(List<MenuTreeNode> children) {
        return new MenuTreeNode(1L, 0L, "系统管理", "/system", "Setting", 1, 1, null, 1, children);
    }

    @Test
    void permissionTypeConstantsMatchMigrationData() {
        // 与 V4 初始化数据的 type 值保持一致，防止误改
        assertEquals(1, SysPermission.TYPE_MENU);
        assertEquals(2, SysPermission.TYPE_API);
    }
}
