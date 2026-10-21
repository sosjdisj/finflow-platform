package org.example.finflowbackend.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.system.SystemErrorCode;
import org.example.finflowbackend.system.dto.MenuCreateRequest;
import org.example.finflowbackend.system.dto.MenuTreeNode;
import org.example.finflowbackend.system.dto.MenuUpdateRequest;
import org.example.finflowbackend.system.entity.SysMenu;
import org.example.finflowbackend.system.mapper.SysMenuMapper;
import org.example.finflowbackend.system.mapper.SysPermissionMapper;
import org.example.finflowbackend.system.service.MenuService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单管理实现。树形结构在内存组装（菜单量级小）；
 * 父节点与关联权限点在写入时校验，更新时做父链环检测。
 */
@Service
public class MenuServiceImpl implements MenuService {

    private final SysMenuMapper menuMapper;
    private final SysPermissionMapper permissionMapper;

    public MenuServiceImpl(SysMenuMapper menuMapper, SysPermissionMapper permissionMapper) {
        this.menuMapper = menuMapper;
        this.permissionMapper = permissionMapper;
    }

    @Override
    public List<MenuTreeNode> tree() {
        List<SysMenu> all = menuMapper.selectList(
                Wrappers.<SysMenu>query().orderByAsc("sort_no").orderByAsc("id"));
        Set<Long> allIds = all.stream().map(SysMenu::getId).collect(Collectors.toSet());
        Map<Long, List<MenuTreeNode>> childrenByParent = new LinkedHashMap<>();
        for (SysMenu menu : all) {
            Long parentId = normalizeParent(menu.getParentId());
            if (parentId != 0 && !allIds.contains(parentId)) {
                // 脏数据兜底：父节点不存在时挂到根，避免节点丢失
                parentId = 0L;
            }
            childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(toNode(menu));
        }
        return buildTree(0L, childrenByParent);
    }

    @Override
    public MenuTreeNode create(MenuCreateRequest request) {
        validateParent(request.parentId(), null);
        validatePermission(request.permissionId());
        SysMenu menu = new SysMenu();
        menu.setParentId(normalizeParent(request.parentId()));
        menu.setTitle(request.title());
        menu.setPath(request.path());
        menu.setIcon(request.icon());
        menu.setMenuType(request.menuType());
        menu.setSortNo(request.sortNo());
        menu.setPermissionId(request.permissionId());
        menu.setVisible(request.visible());
        menu.setStatus(1);
        menuMapper.insert(menu);
        return toNode(menu);
    }

    @Override
    public MenuTreeNode update(Long id, MenuUpdateRequest request) {
        requireMenu(id);
        validateParent(request.parentId(), id);
        validatePermission(request.permissionId());
        SysMenu update = new SysMenu();
        update.setId(id);
        update.setParentId(normalizeParent(request.parentId()));
        update.setTitle(request.title());
        update.setPath(request.path());
        update.setIcon(request.icon());
        update.setMenuType(request.menuType());
        update.setSortNo(request.sortNo());
        update.setPermissionId(request.permissionId());
        update.setVisible(request.visible());
        update.setStatus(request.status());
        menuMapper.updateById(update);
        return toNode(requireMenu(id));
    }

    @Override
    public void delete(Long id) {
        requireMenu(id);
        Long children = menuMapper.selectCount(Wrappers.<SysMenu>query().eq("parent_id", id));
        if (children != null && children > 0) {
            throw new BusinessException(SystemErrorCode.MENU_HAS_CHILDREN);
        }
        menuMapper.deleteById(id);
    }

    /** 递归组树：排序已在 SQL 保证，父子关系按分组直接取。 */
    private List<MenuTreeNode> buildTree(Long parentId, Map<Long, List<MenuTreeNode>> childrenByParent) {
        List<MenuTreeNode> nodes = childrenByParent.getOrDefault(parentId, List.of());
        return nodes.stream()
                .map(node -> new MenuTreeNode(node.id(), node.parentId(), node.title(), node.path(), node.icon(),
                        node.menuType(), node.sortNo(), node.permissionId(), node.visible(),
                        buildTree(node.id(), childrenByParent)))
                .toList();
    }

    /** 父节点必须存在；更新时父链不允许成环。 */
    private void validateParent(Long parentId, Long selfId) {
        Long cursor = normalizeParent(parentId);
        if (cursor == 0) {
            return;
        }
        while (cursor != null && cursor != 0) {
            if (Objects.equals(cursor, selfId)) {
                throw new BusinessException(SystemErrorCode.MENU_PARENT_CYCLE);
            }
            SysMenu parent = menuMapper.selectById(cursor);
            if (parent == null) {
                throw new BusinessException(SystemErrorCode.MENU_NOT_FOUND);
            }
            cursor = normalizeParent(parent.getParentId());
        }
    }

    private void validatePermission(Long permissionId) {
        if (permissionId != null && permissionMapper.selectById(permissionId) == null) {
            throw new BusinessException(SystemErrorCode.MENU_PERMISSION_INVALID);
        }
    }

    private SysMenu requireMenu(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException(SystemErrorCode.MENU_NOT_FOUND);
        }
        return menu;
    }

    private Long normalizeParent(Long parentId) {
        return parentId == null ? 0L : parentId;
    }

    private MenuTreeNode toNode(SysMenu menu) {
        return new MenuTreeNode(menu.getId(), menu.getParentId(), menu.getTitle(), menu.getPath(), menu.getIcon(),
                menu.getMenuType(), menu.getSortNo(), menu.getPermissionId(), menu.getVisible(), List.of());
    }
}
