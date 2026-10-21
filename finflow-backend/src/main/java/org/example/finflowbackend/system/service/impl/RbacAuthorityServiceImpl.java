package org.example.finflowbackend.system.service.impl;

import org.example.finflowbackend.system.dto.MenuTreeNode;
import org.example.finflowbackend.system.entity.SysPermission;
import org.example.finflowbackend.system.mapper.SysPermissionMapper;
import org.example.finflowbackend.system.mapper.SysRoleMapper;
import org.example.finflowbackend.system.service.MenuService;
import org.example.finflowbackend.system.service.RbacAuthorityService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RBAC 授权查询实现。权限的唯一特判点：内置超级管理员（SUPER_ADMIN）不插
 * 角色-权限关联，授权时统一返回全量启用权限码——新增权限点超管自动拥有。
 */
@Service
public class RbacAuthorityServiceImpl implements RbacAuthorityService {

    /** 内置超管角色编码，与 V4 迁移脚本中的初始化数据一致。 */
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** 菜单类型：页面（叶子），需要权限点命中才可见。 */
    private static final int MENU_TYPE_PAGE = 2;

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final MenuService menuService;

    public RbacAuthorityServiceImpl(SysRoleMapper roleMapper, SysPermissionMapper permissionMapper,
                                    MenuService menuService) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.menuService = menuService;
    }

    @Override
    public Set<String> loadRoleCodes(Long userId) {
        List<String> codes = roleMapper.selectRoleCodesByUserId(userId);
        return codes == null ? Set.of() : Set.copyOf(codes);
    }

    @Override
    public Set<String> loadPermissionCodes(Long userId) {
        List<String> codes = loadRoleCodes(userId).contains(SUPER_ADMIN)
                ? permissionMapper.selectAllEnabledCodes()
                : permissionMapper.selectPermissionCodesByUserId(userId);
        return codes == null ? Set.of() : Set.copyOf(codes);
    }

    @Override
    public List<MenuTreeNode> loadMenuTree(Long userId) {
        Set<String> owned = loadPermissionCodes(userId);
        Map<Long, String> codeById = permissionMapper.selectList(null)
                .stream()
                .collect(Collectors.toMap(SysPermission::getId, SysPermission::getCode, (a, b) -> a));
        return filterTree(menuService.tree(), codeById, owned);
    }

    /** 页面菜单要求权限点命中；目录保留当且仅当过滤后仍有子节点。 */
    private List<MenuTreeNode> filterTree(List<MenuTreeNode> nodes, Map<Long, String> codeById, Set<String> owned) {
        List<MenuTreeNode> result = new ArrayList<>();
        for (MenuTreeNode node : nodes) {
            if (node.menuType() != null && node.menuType() == MENU_TYPE_PAGE) {
                String code = codeById.get(node.permissionId());
                if (code != null && owned.contains(code)) {
                    result.add(node);
                }
            } else {
                List<MenuTreeNode> children = node.children() == null
                        ? List.of()
                        : filterTree(node.children(), codeById, owned);
                if (!children.isEmpty()) {
                    result.add(new MenuTreeNode(node.id(), node.parentId(), node.title(), node.path(),
                            node.icon(), node.menuType(), node.sortNo(), node.permissionId(),
                            node.visible(), children));
                }
            }
        }
        return result;
    }
}
