package org.example.finflowbackend.system.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 菜单树节点（children 为空时不输出）。
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MenuTreeNode(Long id, Long parentId, String title, String path, String icon,
                           Integer menuType, Integer sortNo, Long permissionId, Integer visible,
                           List<MenuTreeNode> children) {
}
