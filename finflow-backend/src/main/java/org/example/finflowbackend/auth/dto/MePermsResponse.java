package org.example.finflowbackend.auth.dto;

import java.util.Set;

/**
 * 当前用户的角色与权限编码集合，供前端动态菜单与按钮权限控制（S07 使用）。
 */
public record MePermsResponse(Set<String> roles, Set<String> permissions) {
}
