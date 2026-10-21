package org.example.finflowbackend.system.service;

import org.example.finflowbackend.system.dto.MenuCreateRequest;
import org.example.finflowbackend.system.dto.MenuTreeNode;
import org.example.finflowbackend.system.dto.MenuUpdateRequest;

import java.util.List;

/**
 * 菜单管理：树形展示结构，权限可见性语义全部在 sys_permission。
 */
public interface MenuService {

    List<MenuTreeNode> tree();

    MenuTreeNode create(MenuCreateRequest request);

    MenuTreeNode update(Long id, MenuUpdateRequest request);

    void delete(Long id);
}
