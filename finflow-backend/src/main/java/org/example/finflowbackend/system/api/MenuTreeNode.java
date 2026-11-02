package org.example.finflowbackend.system.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 菜单树节点（children 为空时不输出）。M03 facade：auth 组装用户菜单时读取。
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MenuTreeNode(Long id, Long parentId, String title, String path, String icon,
                           Integer menuType, Integer sortNo, Long permissionId, Integer visible,
                           List<MenuTreeNode> children) {
}
