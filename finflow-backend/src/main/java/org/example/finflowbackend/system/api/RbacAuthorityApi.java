package org.example.finflowbackend.system.api;

import java.util.List;
import java.util.Set;

/**
 * RBAC 授权查询的对外能力（M03 facade）：auth 模块取用户角色、权限编码与可见菜单。
 * 内置超级管理员（SUPER_ADMIN）在实现内统一按全量启用权限处理。
 */
public interface RbacAuthorityApi {

    /** 用户的启用角色编码集合。 */
    Set<String> loadRoleCodes(Long userId);

    /** 用户的启用权限编码集合（超管返回全量启用权限）。 */
    Set<String> loadPermissionCodes(Long userId);

    /** 用户可见菜单树：按权限点过滤，过滤后无子节点的目录被剔除。 */
    List<MenuTreeNode> loadMenuTree(Long userId);
}
