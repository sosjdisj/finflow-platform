package org.example.finflowbackend.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.system.dto.MenuCreateRequest;
import org.example.finflowbackend.system.dto.MenuTreeNode;
import org.example.finflowbackend.system.dto.MenuUpdateRequest;
import org.example.finflowbackend.system.service.MenuService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单管理接口。
 */
@RestController
@Tag(name = "菜单管理")
@RequestMapping("/api/v1/system/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:menu:list')")
    public ApiResponse<List<MenuTreeNode>> tree() {
        return ApiResponse.ok(menuService.tree());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:menu:create')")
    public ApiResponse<MenuTreeNode> create(@Valid @RequestBody MenuCreateRequest request) {
        return ApiResponse.ok(menuService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:update')")
    public ApiResponse<MenuTreeNode> update(@PathVariable Long id, @Valid @RequestBody MenuUpdateRequest request) {
        return ApiResponse.ok(menuService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:menu:delete')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return ApiResponse.ok();
    }
}
