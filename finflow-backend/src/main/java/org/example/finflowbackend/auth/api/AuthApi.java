package org.example.finflowbackend.auth.api;

/**
 * 用户信息的对外能力（M03 facade）：其他模块校验用户存在性 / 读取用户展示信息时只经此接口，
 * 不直接读 auth 模块的表。登录、注册、会话等后台能力留在模块内部的 {@code AuthService}。
 */
public interface AuthApi {

    /** 获取用户信息（不含密码字段）；用户不存在抛 404。 */
    UserInfoResponse getUserInfo(Long userId);
}
