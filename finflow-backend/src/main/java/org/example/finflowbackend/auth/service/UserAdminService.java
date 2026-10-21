package org.example.finflowbackend.auth.service;

import org.example.finflowbackend.auth.dto.UserAdminResponse;
import org.example.finflowbackend.auth.dto.UserCreateRequest;
import org.example.finflowbackend.auth.dto.UserPasswordRequest;
import org.example.finflowbackend.auth.dto.UserStatusRequest;
import org.example.finflowbackend.auth.dto.UserUpdateRequest;
import org.example.finflowbackend.common.api.PageResponse;

/**
 * 用户管理（管理后台用）：sys_user 归 auth 模块读写，路由挂 /api/v1/system/users。
 */
public interface UserAdminService {

    /** 分页查询，keyword 模糊匹配用户名/显示名。 */
    PageResponse<UserAdminResponse> page(long page, long size, String keyword);

    /** 创建用户：用户名唯一，密码 BCrypt 存储。 */
    UserAdminResponse create(UserCreateRequest request);

    /** 编辑昵称/邮箱/手机号。 */
    UserAdminResponse update(Long id, UserUpdateRequest request);

    /** 启用/禁用；禁用同时吊销其全部 refresh token；不允许禁用操作者本人。 */
    UserAdminResponse updateStatus(Long operatorId, Long id, UserStatusRequest request);

    /** 重置密码并吊销该用户全部 refresh token。 */
    void resetPassword(Long id, UserPasswordRequest request);
}
