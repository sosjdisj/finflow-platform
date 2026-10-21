package org.example.finflowbackend.auth.service;

import org.example.finflowbackend.auth.dto.ChangePasswordRequest;
import org.example.finflowbackend.auth.dto.LoginLogResponse;
import org.example.finflowbackend.auth.dto.LoginRequest;
import org.example.finflowbackend.auth.dto.RegisterRequest;
import org.example.finflowbackend.auth.dto.SessionResponse;
import org.example.finflowbackend.auth.dto.TokenResponse;
import org.example.finflowbackend.auth.dto.UserInfoResponse;
import org.example.finflowbackend.common.api.PageResponse;

import java.util.List;

/**
 * 认证服务：auth 模块对外暴露的接口，其他模块只能通过它取用户信息。
 */
public interface AuthService {

    /** 注册：用户名唯一，密码 BCrypt 加密存储。 */
    UserInfoResponse register(RegisterRequest request);

    /**
     * 登录：校验锁定 → 验证码 → 凭证（顺序固定），成功/失败均写登录日志，
     * 密码错误累加锁定计数，成功后签发 access + refresh 双 token 并记录设备。
     */
    TokenResponse login(LoginRequest request, String ip, String userAgent);

    /** 刷新：校验 refresh token 有效后轮换出一对新 token（设备信息沿用本次请求）。 */
    TokenResponse refresh(String refreshToken, String ip, String userAgent);

    /** 登出：吊销指定 refresh token（幂等，无效令牌静默处理）。 */
    void logout(String refreshToken);

    /** 修改密码：校验原密码，成功后吊销该用户全部 refresh token。 */
    void changePassword(Long userId, ChangePasswordRequest request);

    /** 获取用户信息（不含密码字段）。 */
    UserInfoResponse getUserInfo(Long userId);

    /** 当前登录设备列表：活跃 refresh token，id 即踢下线用的会话 ID。 */
    List<SessionResponse> listSessions(Long userId);

    /** 踢下线：吊销本人指定会话；操作他人或不存在的会话一律 NOT_FOUND。 */
    void revokeSession(Long userId, Long sessionId);

    /** 本人登录日志（分页，时间倒序）。 */
    PageResponse<LoginLogResponse> listLoginLogs(Long userId, long page, long size);
}
