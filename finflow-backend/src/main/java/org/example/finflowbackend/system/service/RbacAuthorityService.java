package org.example.finflowbackend.system.service;

import org.example.finflowbackend.system.dto.MenuTreeNode;

import java.util.List;
import java.util.Set;

/**
 * RBAC 授权查询：auth 模块（JWT 过滤器 / 当前用户接口）经此接口取用户的角色、权限编码与可见菜单，
 * 模块间不直接读对方表。内置超级管理员（SUPER_ADMIN）在此统一按全量启用权限处理。
 */
public interface RbacAuthorityService {

    /** 用户的启用角色编码集合。 */
    Set<String> loadRoleCodes(Long userId);

    /** 用户的启用权限编码集合（超管返回全量启用权限）。 */
    Set<String> loadPermissionCodes(Long userId);

    /**
     * 用户可见菜单树：页面菜单按其权限点是否命中用户权限集过滤，
     * 目录在过滤后无子节点时剔除（超管经全量权限自动可见全部）。
     */
    List<MenuTreeNode> loadMenuTree(Long userId);
}
