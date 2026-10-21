package org.example.finflowbackend.auth.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.finflowbackend.auth.dto.CaptchaResponse;
import org.example.finflowbackend.auth.dto.ChangePasswordRequest;
import org.example.finflowbackend.auth.dto.LoginLogResponse;
import org.example.finflowbackend.auth.dto.LoginRequest;
import org.example.finflowbackend.auth.dto.MePermsResponse;
import org.example.finflowbackend.auth.dto.RefreshTokenRequest;
import org.example.finflowbackend.auth.dto.RegisterRequest;
import org.example.finflowbackend.auth.dto.SessionResponse;
import org.example.finflowbackend.auth.dto.TokenResponse;
import org.example.finflowbackend.auth.dto.UserInfoResponse;
import org.example.finflowbackend.auth.service.AuthService;
import org.example.finflowbackend.auth.service.CaptchaService;
import org.example.finflowbackend.auth.web.ClientInfo;
import org.example.finflowbackend.auth.web.CurrentUserHolder;
import org.example.finflowbackend.common.api.ApiResponse;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.system.dto.MenuTreeNode;
import org.example.finflowbackend.system.service.RbacAuthorityService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口。除 register / login / refresh / logout / captcha 外都需要登录（安全过滤链保证）。
 */
@RestController
@Tag(name = "认证")
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final RbacAuthorityService rbacAuthorityService;

    public AuthController(AuthService authService, CaptchaService captchaService,
                          RbacAuthorityService rbacAuthorityService) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.rbacAuthorityService = rbacAuthorityService;
    }

    /** 图形验证码：登录前获取，一次一用。 */
    @GetMapping("/captcha")
    public ApiResponse<CaptchaResponse> captcha() {
        return ApiResponse.ok(captchaService.generate());
    }

    @PostMapping("/register")
    public ApiResponse<UserInfoResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        ClientInfo client = ClientInfo.from(httpRequest);
        return ApiResponse.ok(authService.login(request, client.ip(), client.userAgent()));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                              HttpServletRequest httpRequest) {
        ClientInfo client = ClientInfo.from(httpRequest);
        return ApiResponse.ok(authService.refresh(request.refreshToken(), client.ip(), client.userAgent()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok();
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(CurrentUserHolder.current().userId(), request);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> me() {
        return ApiResponse.ok(authService.getUserInfo(CurrentUserHolder.current().userId()));
    }

    /** 当前用户的角色与权限编码：登录即可查自己，前端动态菜单与按钮控制的数据源。 */
    @GetMapping("/me/perms")
    public ApiResponse<MePermsResponse> myPerms() {
        Long userId = CurrentUserHolder.current().userId();
        return ApiResponse.ok(new MePermsResponse(
                rbacAuthorityService.loadRoleCodes(userId),
                rbacAuthorityService.loadPermissionCodes(userId)));
    }

    /** 当前用户可见菜单树：按权限点过滤，前端侧边栏与动态路由的数据源。 */
    @GetMapping("/me/menus")
    public ApiResponse<List<MenuTreeNode>> myMenus() {
        return ApiResponse.ok(rbacAuthorityService.loadMenuTree(CurrentUserHolder.current().userId()));
    }

    /** 当前登录设备列表。 */
    @GetMapping("/sessions")
    public ApiResponse<List<SessionResponse>> sessions() {
        return ApiResponse.ok(authService.listSessions(CurrentUserHolder.current().userId()));
    }

    /** 踢下线：吊销本人指定会话的 refresh token。 */
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> revokeSession(@PathVariable Long id) {
        authService.revokeSession(CurrentUserHolder.current().userId(), id);
        return ApiResponse.ok();
    }

    /** 本人登录日志（分页）。 */
    @GetMapping("/login-logs")
    public ApiResponse<PageResponse<LoginLogResponse>> loginLogs(@RequestParam(defaultValue = "1") long page,
                                                                 @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(authService.listLoginLogs(CurrentUserHolder.current().userId(), page, size));
    }
}
