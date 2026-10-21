package org.example.finflowbackend.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.auth.AuthErrorCode;
import org.example.finflowbackend.auth.dto.ChangePasswordRequest;
import org.example.finflowbackend.auth.dto.LoginLogResponse;
import org.example.finflowbackend.auth.dto.LoginRequest;
import org.example.finflowbackend.auth.dto.RegisterRequest;
import org.example.finflowbackend.auth.dto.SessionResponse;
import org.example.finflowbackend.auth.dto.TokenResponse;
import org.example.finflowbackend.auth.dto.UserInfoResponse;
import org.example.finflowbackend.auth.entity.SysLoginLog;
import org.example.finflowbackend.auth.entity.SysRefreshToken;
import org.example.finflowbackend.auth.entity.SysUser;
import org.example.finflowbackend.auth.mapper.SysLoginLogMapper;
import org.example.finflowbackend.auth.mapper.SysRefreshTokenMapper;
import org.example.finflowbackend.auth.mapper.SysUserMapper;
import org.example.finflowbackend.auth.security.MfaHook;
import org.example.finflowbackend.auth.service.AuthService;
import org.example.finflowbackend.auth.service.CaptchaService;
import org.example.finflowbackend.auth.service.LoginLockService;
import org.example.finflowbackend.auth.token.JwtTokenProvider;
import org.example.finflowbackend.common.api.ErrorCode;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证服务实现。简单 CRUD 走 BaseMapper，不手写 XML。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final int STATUS_ENABLED = 1;
    private static final int LOGIN_SUCCESS = 1;
    private static final int LOGIN_FAIL = 0;
    private static final int IP_MAX = 45;
    private static final int USER_AGENT_MAX = 255;
    private static final int DEVICE_NAME_MAX = 128;

    private final SysUserMapper userMapper;
    private final SysRefreshTokenMapper refreshTokenMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final LoginLockService loginLockService;
    private final CaptchaService captchaService;
    private final ObjectProvider<MfaHook> mfaHookProvider;

    public AuthServiceImpl(SysUserMapper userMapper, SysRefreshTokenMapper refreshTokenMapper,
                           SysLoginLogMapper loginLogMapper, PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider, LoginLockService loginLockService,
                           CaptchaService captchaService, ObjectProvider<MfaHook> mfaHookProvider) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.loginLogMapper = loginLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.loginLockService = loginLockService;
        this.captchaService = captchaService;
        this.mfaHookProvider = mfaHookProvider;
    }

    @Override
    @Transactional
    public UserInfoResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (findByUsername(username) != null) {
            throw new BusinessException(AuthErrorCode.USERNAME_EXISTS);
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(StringUtils.hasText(request.nickname()) ? request.nickname().trim() : username);
        user.setEmail(trimToNull(request.email()));
        user.setPhone(trimToNull(request.phone()));
        user.setStatus(STATUS_ENABLED);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发下唯一索引兜底：两人同时注册同一用户名，后来者按已存在处理
            throw new BusinessException(AuthErrorCode.USERNAME_EXISTS);
        }
        return UserInfoResponse.from(user);
    }

    @Override
    public TokenResponse login(LoginRequest request, String ip, String userAgent) {
        String username = request.username().trim();
        loginLockService.checkLocked(username);
        try {
            return doLogin(request, username, ip, userAgent);
        } catch (BusinessException e) {
            if (e.getErrorCode() == AuthErrorCode.LOGIN_FAILED) {
                loginLockService.recordFailure(username);
            }
            saveLoginLog(username, null, LOGIN_FAIL, e.getMessage(), ip, userAgent);
            throw e;
        }
    }

    /** 登录主流程：验证码 → 凭证 → MFA 扩展点 → 日志与令牌。 */
    private TokenResponse doLogin(LoginRequest request, String username, String ip, String userAgent) {
        captchaService.verify(request.captchaId(), request.captchaCode());
        SysUser user = findByUsername(username);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }
        ensureEnabled(user);
        requireMfaAllowed(user, ip);
        touchLastLoginAt(user.getId());
        loginLockService.clear(username);
        TokenResponse tokens = issueTokens(user, ip, userAgent);
        saveLoginLog(username, user.getId(), LOGIN_SUCCESS, null, ip, userAgent);
        return tokens;
    }

    @Override
    @Transactional
    public TokenResponse refresh(String refreshToken, String ip, String userAgent) {
        SysRefreshToken stored = requireEffectiveToken(refreshToken);
        SysUser user = userMapper.selectById(stored.getUserId());
        if (user == null) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        ensureEnabled(user);
        revoke(stored);
        return issueTokens(user, ip, userAgent);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        SysRefreshToken stored = findToken(refreshToken);
        if (stored != null) {
            revoke(stored);
        }
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.OLD_PASSWORD_MISMATCH);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.PASSWORD_SAME_AS_OLD);
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(update);
        revokeAll(userId);
    }

    @Override
    public UserInfoResponse getUserInfo(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return UserInfoResponse.from(user);
    }

    @Override
    public List<SessionResponse> listSessions(Long userId) {
        return refreshTokenMapper.selectList(Wrappers.<SysRefreshToken>lambdaQuery()
                        .eq(SysRefreshToken::getUserId, userId)
                        .isNull(SysRefreshToken::getRevokedAt)
                        .gt(SysRefreshToken::getExpiresAt, LocalDateTime.now())
                        .orderByDesc(SysRefreshToken::getCreatedAt))
                .stream()
                .map(token -> new SessionResponse(token.getId(), token.getDeviceName(),
                        token.getIp(), token.getCreatedAt()))
                .toList();
    }

    @Override
    public void revokeSession(Long userId, Long sessionId) {
        SysRefreshToken stored = refreshTokenMapper.selectById(sessionId);
        // 不区分不存在与他人会话，避免探测他人会话 ID
        if (stored == null || !userId.equals(stored.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        revoke(stored);
    }

    @Override
    public PageResponse<LoginLogResponse> listLoginLogs(Long userId, long page, long size) {
        Page<SysLoginLog> result = loginLogMapper.selectPage(new Page<>(page, size),
                Wrappers.<SysLoginLog>lambdaQuery()
                        .eq(SysLoginLog::getUserId, userId)
                        .orderByDesc(SysLoginLog::getCreatedAt));
        return PageResponse.of(result.convert(LoginLogResponse::from));
    }

    /** MFA 扩展点：未注册实现时直接放行（S06 只预留）。 */
    private void requireMfaAllowed(SysUser user, String ip) {
        MfaHook hook = mfaHookProvider.getIfAvailable();
        if (hook != null && !hook.allowLogin(user, ip)) {
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }
    }

    private void touchLastLoginAt(Long userId) {
        SysUser touch = new SysUser();
        touch.setId(userId);
        touch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(touch);
    }

    /** 成功/失败各记一条登录日志。 */
    private void saveLoginLog(String username, Long userId, int success, String failReason,
                              String ip, String userAgent) {
        SysLoginLog log = new SysLoginLog();
        log.setUsername(username);
        log.setUserId(userId);
        log.setSuccess(success);
        log.setFailReason(failReason);
        log.setIp(truncate(ip, IP_MAX));
        log.setUserAgent(truncate(userAgent, USER_AGENT_MAX));
        loginLogMapper.insert(log);
    }

    /** 签发一对新令牌，并把 refresh token 哈希连同登录设备信息写入库中。 */
    private TokenResponse issueTokens(SysUser user, String ip, String userAgent) {
        String accessToken = tokenProvider.issueAccessToken(user);
        String refreshToken = tokenProvider.newRefreshToken();

        SysRefreshToken entity = new SysRefreshToken();
        entity.setUserId(user.getId());
        entity.setTokenHash(JwtTokenProvider.hash(refreshToken));
        entity.setExpiresAt(LocalDateTime.now().plus(tokenProvider.getRefreshTokenTtl()));
        entity.setDeviceName(truncate(userAgent, DEVICE_NAME_MAX));
        entity.setIp(truncate(ip, IP_MAX));
        refreshTokenMapper.insert(entity);

        return new TokenResponse(accessToken, refreshToken, "Bearer", tokenProvider.getAccessTokenTtl().toSeconds());
    }

    private SysUser findByUsername(String username) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
    }

    private SysRefreshToken findToken(String rawToken) {
        return refreshTokenMapper.selectOne(Wrappers.<SysRefreshToken>lambdaQuery()
                .eq(SysRefreshToken::getTokenHash, JwtTokenProvider.hash(rawToken)));
    }

    /** 取有效令牌：不存在、已吊销、已过期一律按凭证失效处理。 */
    private SysRefreshToken requireEffectiveToken(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        SysRefreshToken stored = findToken(rawToken);
        if (stored == null || stored.getRevokedAt() != null || !stored.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        return stored;
    }

    private void revoke(SysRefreshToken stored) {
        if (stored.getRevokedAt() != null) {
            return;
        }
        SysRefreshToken update = new SysRefreshToken();
        update.setId(stored.getId());
        update.setRevokedAt(LocalDateTime.now());
        refreshTokenMapper.updateById(update);
    }

    /** 吊销该用户全部有效令牌（一次性 UPDATE，避免逐条循环）。 */
    private void revokeAll(Long userId) {
        refreshTokenMapper.update(null, Wrappers.<SysRefreshToken>update()
                .eq("user_id", userId)
                .isNull("revoked_at")
                .set("revoked_at", LocalDateTime.now()));
    }

    private void ensureEnabled(SysUser user) {
        if (user.getStatus() == null || user.getStatus() != STATUS_ENABLED) {
            throw new BusinessException(AuthErrorCode.USER_DISABLED);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
