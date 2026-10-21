package org.example.finflowbackend.system.service.impl;

import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.system.SystemErrorCode;
import org.example.finflowbackend.system.dto.MenuCreateRequest;
import org.example.finflowbackend.system.dto.MenuUpdateRequest;
import org.example.finflowbackend.system.entity.SysMenu;
import org.example.finflowbackend.system.mapper.SysMenuMapper;
import org.example.finflowbackend.system.mapper.SysPermissionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 菜单服务单测：树构建（含孤儿挂根）、父节点校验、环检测、子菜单删除保护。
 */
class MenuServiceImplTest {

    private final SysMenuMapper menuMapper = mock(SysMenuMapper.class);
    private final SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);
    private final MenuServiceImpl service = new MenuServiceImpl(menuMapper, permissionMapper);

    @Test
    void treeBuildsHierarchyAndReattachesOrphans() {
        when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(1L, 0L, "系统管理"),
                menu(10L, 1L, "用户管理"),
                menu(11L, 1L, "角色管理"),
                menu(99L, 404L, "孤儿菜单")));

        var tree = service.tree();

        // 根：系统管理 + 被挂根的孤儿
        assertEquals(2, tree.size());
        var system = tree.stream().filter(n -> n.id().equals(1L)).findFirst().orElseThrow();
        assertEquals(2, system.children().size());
        assertEquals("用户管理", system.children().get(0).title());
    }

    @Test
    void createRejectsUnknownParent() {
        when(menuMapper.selectById(404L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(new MenuCreateRequest(404L, "页面", "/x", null, 2, 1, null, 1)));

        assertEquals(SystemErrorCode.MENU_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void createRejectsUnknownPermission() {
        when(permissionMapper.selectById(999L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(new MenuCreateRequest(0L, "页面", "/x", null, 2, 1, 999L, 1)));

        assertEquals(SystemErrorCode.MENU_PERMISSION_INVALID, error.getErrorCode());
    }

    @Test
    void updateRejectsParentCycle() {
        // 1 的父是 2，2 的父是 1：把 1 的父改成 2 会成环
        when(menuMapper.selectById(1L)).thenReturn(menu(1L, 2L, "节点1"));
        when(menuMapper.selectById(2L)).thenReturn(menu(2L, 1L, "节点2"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(1L, new MenuUpdateRequest(2L, "节点1", null, null, 1, 0, null, 1, 1)));

        assertEquals(SystemErrorCode.MENU_PARENT_CYCLE, error.getErrorCode());
        verify(menuMapper, never()).updateById(any(SysMenu.class));
    }

    @Test
    void updateRejectsSelfAsParent() {
        when(menuMapper.selectById(1L)).thenReturn(menu(1L, 0L, "节点1"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(1L, new MenuUpdateRequest(1L, "节点1", null, null, 1, 0, null, 1, 1)));

        assertEquals(SystemErrorCode.MENU_PARENT_CYCLE, error.getErrorCode());
    }

    @Test
    void deleteRejectsMenuWithChildren() {
        when(menuMapper.selectById(1L)).thenReturn(menu(1L, 0L, "目录"));
        when(menuMapper.selectCount(any())).thenReturn(2L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.delete(1L));

        assertEquals(SystemErrorCode.MENU_HAS_CHILDREN, error.getErrorCode());
        verify(menuMapper, never()).deleteById(1L);
    }

    private SysMenu menu(Long id, Long parentId, String title) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setTitle(title);
        menu.setMenuType(parentId == 0 ? 1 : 2);
        menu.setSortNo(0);
        menu.setVisible(1);
        menu.setStatus(1);
        return menu;
    }
}
