package org.example.finflowbackend.system;

import org.example.finflowbackend.common.api.ErrorCodeSpec;

/**
 * system 模块错误码（42xxx 段）。
 * 模块业务码定义在模块内，不进公共 ErrorCode 枚举。
 */
public enum SystemErrorCode implements ErrorCodeSpec {

    ROLE_CODE_EXISTS(42001, "角色编码已存在", 409),
    ROLE_NOT_FOUND(42002, "角色不存在", 404),
    ROLE_BUILT_IN_PROTECTED(42003, "内置角色不允许修改、删除或分配权限", 400),
    PERMISSION_CODE_EXISTS(42004, "权限编码已存在", 409),
    PERMISSION_NOT_FOUND(42005, "权限不存在", 404),
    PERMISSION_TYPE_INVALID(42006, "权限类型不合法", 400),
    PERMISSION_IN_USE(42007, "权限已被角色或菜单引用，请先解除关联", 409),
    MENU_NOT_FOUND(42008, "菜单不存在", 404),
    MENU_HAS_CHILDREN(42009, "存在子菜单，无法删除", 409),
    MENU_PERMISSION_INVALID(42010, "关联的权限点不存在", 400),
    MENU_PARENT_CYCLE(42011, "父菜单不能是自身或其后代", 400),
    CONFIG_KEY_NOT_FOUND(42012, "配置项不存在", 404),
    CONFIG_VALUE_INVALID(42013, "配置值不合法", 400),
    FILE_EMPTY(42014, "上传文件为空", 400),
    FILE_TYPE_INVALID(42015, "文件业务类型不合法", 400),
    FILE_NOT_FOUND(42016, "文件不存在", 404),
    FILE_STORE_FAILED(42017, "文件存储失败", 500);

    private final int code;
    private final String message;
    private final int httpStatus;

    SystemErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
