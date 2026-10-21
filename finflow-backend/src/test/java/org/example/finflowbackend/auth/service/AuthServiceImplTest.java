package org.example.finflowbackend.auth.service;

import org.example.finflowbackend.auth.AuthErrorCode;
import org.example.finflowbackend.auth.dto.ChangePasswordRequest;
import org.example.finflowbackend.auth.dto.LoginRequest;
import org.example.finflowbackend.auth.dto.RegisterRequest;
import org.example.finflowbackend.auth.dto.SessionResponse;
import org.example.finflowbackend.auth.dto.TokenResponse;
import org.example.finflowbackend.auth.entity.SysLoginLog;
import org.example.finflowbackend.auth.entity.SysRefreshToken;
import org.example.finflowbackend.auth.entity.SysUser;
import org.example.finflowbackend.auth.mapper.SysLoginLogMapper;
import org.example.finflowbackend.auth.mapper.SysRefreshTokenMapper;
import org.example.finflowbackend.auth.mapper.SysUserMapper;
import org.example.finflowbackend.auth.security.MfaHook;
import org.example.finflowbackend.auth.service.impl.AuthServiceImpl;
import org.example.finflowbackend.auth.token.JwtProperties;
import org.example.finflowbackend.auth.token.JwtTokenProvider;
import org.example.finflowbackend.common.api.ErrorCode;
import org.example.finflowbackend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单测：注册唯一性、登录校验（锁定/验证码/日志）、刷新轮换、修改密码、设备会话。
 */
class AuthServiceImplTest {

    private static final String IP = "10.0.0.1";
    private static final String USER_AGENT = "JUnit-Agent";

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysRefreshTokenMapper refreshTokenMapper = mock(SysRefreshTokenMapper.class);
    private final SysLoginLogMapper loginLogMapper = mock(SysLoginLogMapper.class);
    private final LoginLockService loginLockService = mock(LoginLockService.class);
    private final CaptchaService captchaService = mock(CaptchaService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<MfaHook> mfaHookProvider = mock(ObjectProvider.class);
    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(properties());
    private final AuthServiceImpl service = new AuthServiceImpl(userMapper, refreshTokenMapper, loginLogMapper,
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(), tokenProvider,
            loginLockService, captchaService, mfaHookProvider);

    @Test
    void registerRejectsDuplicatedUsername() {
        when(userMapper.selectOne(any())).thenReturn(existingUser("alice", "whatever"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.register(new RegisterRequest("alice", "password123", null, null, null)));

        assertEquals(AuthErrorCode.USERNAME_EXISTS, error.getErrorCode());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void registerStoresBcryptHashNotNull() {
        when(userMapper.selectOne(any())).thenReturn(null);

        service.register(new RegisterRequest("alice", "password123", " Alice ", null, null));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        SysUser saved = captor.getValue();
        assertEquals("alice", saved.getUsername());
        assertEquals("Alice", saved.getNickname());
        assertEquals(1, saved.getStatus());
        assertNotEquals("password123", saved.getPasswordHash());
        assertTrue(saved.getPasswordHash().startsWith("$2"));
    }

    @Test
    void loginRejectsWrongPasswordAndRecordsFailure() {
        when(userMapper.selectOne(any())).thenReturn(existingUser("alice", "$2a$10$unknownhash"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("alice", "wrong-password", "captcha-1", "AB2D"), IP, USER_AGENT));

        assertEquals(AuthErrorCode.LOGIN_FAILED, error.getErrorCode());
        verify(loginLockService).recordFailure("alice");
        verify(loginLogMapper).insert(any(SysLoginLog.class));
    }

    @Test
    void loginRejectsUnknownUser() {
        when(userMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("nobody", "password123", "captcha-1", "AB2D"), IP, USER_AGENT));

        assertEquals(AuthErrorCode.LOGIN_FAILED, error.getErrorCode());
        verify(loginLockService).recordFailure("nobody");
    }

    @Test
    void loginWritesFailureLogWithClientInfo() {
        when(userMapper.selectOne(any())).thenReturn(existingUser("alice", "$2a$10$unknownhash"));

        assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("alice", "wrong-password", "captcha-1", "AB2D"), IP, USER_AGENT));

        ArgumentCaptor<SysLoginLog> captor = ArgumentCaptor.forClass(SysLoginLog.class);
        verify(loginLogMapper).insert(captor.capture());
        SysLoginLog log = captor.getValue();
        assertEquals(0, log.getSuccess());
        assertEquals("alice", log.getUsername());
        assertNull(log.getUserId());
        assertEquals(IP, log.getIp());
        assertEquals(USER_AGENT, log.getUserAgent());
    }

    @Test
    void loginReturnsTokenPairUpdatesLastLoginAndWritesSuccessLog() {
        String rawPassword = "password123";
        SysUser user = existingUser("alice", encoded(rawPassword));
        when(userMapper.selectOne(any())).thenReturn(user);

        TokenResponse response = service.login(new LoginRequest("alice", rawPassword, "captcha-1", "AB2D"), IP, USER_AGENT);

        assertNotNull(response.accessToken());
        assertNotNull(response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(Duration.ofMinutes(30).toSeconds(), response.expiresIn());

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertNotNull(captor.getValue().getLastLoginAt());

        ArgumentCaptor<SysRefreshToken> tokenCaptor = ArgumentCaptor.forClass(SysRefreshToken.class);
        verify(refreshTokenMapper).insert(tokenCaptor.capture());
        assertEquals(JwtTokenProvider.hash(response.refreshToken()), tokenCaptor.getValue().getTokenHash());
        assertEquals(USER_AGENT, tokenCaptor.getValue().getDeviceName());
        assertEquals(IP, tokenCaptor.getValue().getIp());

        ArgumentCaptor<SysLoginLog> logCaptor = ArgumentCaptor.forClass(SysLoginLog.class);
        verify(loginLogMapper).insert(logCaptor.capture());
        assertEquals(1, logCaptor.getValue().getSuccess());
        assertEquals(user.getId(), logCaptor.getValue().getUserId());

        verify(loginLockService).clear("alice");
    }

    @Test
    void refreshRotatesTokenAndRevokesTheOldOne() {
        SysUser user = existingUser("alice", encoded("password123"));
        String rawRefreshToken = tokenProvider.newRefreshToken();
        when(refreshTokenMapper.selectOne(any())).thenReturn(effectiveToken(user.getId(), rawRefreshToken));
        when(userMapper.selectById(user.getId())).thenReturn(user);

        TokenResponse response = service.refresh(rawRefreshToken, IP, USER_AGENT);

        assertNotEquals(rawRefreshToken, response.refreshToken());
        ArgumentCaptor<SysRefreshToken> revoked = ArgumentCaptor.forClass(SysRefreshToken.class);
        verify(refreshTokenMapper).updateById(revoked.capture());
        assertNotNull(revoked.getValue().getRevokedAt());
        verify(refreshTokenMapper).insert(any(SysRefreshToken.class));
    }

    @Test
    void refreshRejectsRevokedToken() {
        SysRefreshToken stored = effectiveToken(7L, "whatever");
        stored.setRevokedAt(LocalDateTime.now());
        when(refreshTokenMapper.selectOne(any())).thenReturn(stored);

        BusinessException error = assertThrows(BusinessException.class, () -> service.refresh("whatever", IP, USER_AGENT));

        assertEquals(AuthErrorCode.REFRESH_TOKEN_INVALID, error.getErrorCode());
        verify(refreshTokenMapper, never()).insert(any(SysRefreshToken.class));
    }

    @Test
    void refreshRejectsExpiredToken() {
        SysRefreshToken stored = effectiveToken(7L, "whatever");
        stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(refreshTokenMapper.selectOne(any())).thenReturn(stored);

        BusinessException error = assertThrows(BusinessException.class, () -> service.refresh("whatever", IP, USER_AGENT));

        assertEquals(AuthErrorCode.REFRESH_TOKEN_INVALID, error.getErrorCode());
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        String rawPassword = "password123";
        when(userMapper.selectById(7L)).thenReturn(existingUser("alice", encoded(rawPassword)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changePassword(7L, new ChangePasswordRequest("wrong-password", "newpassword123")));

        assertEquals(AuthErrorCode.OLD_PASSWORD_MISMATCH, error.getErrorCode());
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void changePasswordRevokesAllRefreshTokens() {
        String rawPassword = "password123";
        when(userMapper.selectById(7L)).thenReturn(existingUser("alice", encoded(rawPassword)));

        service.changePassword(7L, new ChangePasswordRequest(rawPassword, "newpassword123"));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertTrue(captor.getValue().getPasswordHash().startsWith("$2"));
        verify(refreshTokenMapper).update(any(), any());
    }

    @Test
    void listSessionsMapsActiveTokens() {
        SysRefreshToken token = effectiveToken(7L, "whatever");
        token.setDeviceName("Chrome/120");
        token.setIp("10.0.0.2");
        when(refreshTokenMapper.selectList(any())).thenReturn(List.of(token));

        List<SessionResponse> sessions = service.listSessions(7L);

        assertEquals(1, sessions.size());
        assertEquals(100L, sessions.getFirst().id());
        assertEquals("Chrome/120", sessions.getFirst().deviceName());
        assertEquals("10.0.0.2", sessions.getFirst().ip());
    }

    @Test
    void revokeSessionRevokesOwnActiveToken() {
        when(refreshTokenMapper.selectById(100L)).thenReturn(effectiveToken(7L, "whatever"));

        service.revokeSession(7L, 100L);

        ArgumentCaptor<SysRefreshToken> captor = ArgumentCaptor.forClass(SysRefreshToken.class);
        verify(refreshTokenMapper).updateById(captor.capture());
        assertNotNull(captor.getValue().getRevokedAt());
    }

    @Test
    void revokeSessionRejectsForeignOrMissingSession() {
        when(refreshTokenMapper.selectById(100L)).thenReturn(effectiveToken(8L, "whatever"));
        when(refreshTokenMapper.selectById(999L)).thenReturn(null);

        BusinessException foreign = assertThrows(BusinessException.class, () -> service.revokeSession(7L, 100L));
        BusinessException missing = assertThrows(BusinessException.class, () -> service.revokeSession(7L, 999L));

        assertEquals(ErrorCode.NOT_FOUND, foreign.getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND, missing.getErrorCode());
        verify(refreshTokenMapper, never()).updateById(any(SysRefreshToken.class));
    }

    private String encoded(String rawPassword) {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(rawPassword);
    }

    private static SysUser existingUser(String username, String passwordHash) {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername(username);
        user.setNickname(username);
        user.setPasswordHash(passwordHash);
        user.setStatus(1);
        return user;
    }

    private static SysRefreshToken effectiveToken(Long userId, String rawToken) {
        SysRefreshToken token = new SysRefreshToken();
        token.setId(100L);
        token.setUserId(userId);
        token.setTokenHash(JwtTokenProvider.hash(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        return token;
    }

    private static JwtProperties properties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-0123456789abcdefghijklmn");
        properties.setAccessTokenTtl(Duration.ofMinutes(30));
        properties.setRefreshTokenTtl(Duration.ofDays(7));
        return properties;
    }
}
