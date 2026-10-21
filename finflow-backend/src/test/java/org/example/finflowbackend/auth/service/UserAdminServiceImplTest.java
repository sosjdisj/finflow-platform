package org.example.finflowbackend.auth.service;

import org.example.finflowbackend.auth.AuthErrorCode;
import org.example.finflowbackend.auth.dto.UserCreateRequest;
import org.example.finflowbackend.auth.dto.UserPasswordRequest;
import org.example.finflowbackend.auth.dto.UserStatusRequest;
import org.example.finflowbackend.auth.dto.UserUpdateRequest;
import org.example.finflowbackend.auth.entity.SysUser;
import org.example.finflowbackend.auth.mapper.SysRefreshTokenMapper;
import org.example.finflowbackend.auth.mapper.SysUserMapper;
import org.example.finflowbackend.auth.service.impl.UserAdminServiceImpl;
import org.example.finflowbackend.common.api.ErrorCode;
import org.example.finflowbackend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户管理单测：唯一性、禁用保护与吊销令牌、重置密码。
 */
class UserAdminServiceImplTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysRefreshTokenMapper refreshTokenMapper = mock(SysRefreshTokenMapper.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UserAdminServiceImpl service = new UserAdminServiceImpl(userMapper, refreshTokenMapper, encoder);

    @Test
    void createRejectsDuplicatedUsername() {
        when(userMapper.selectOne(any())).thenReturn(user(7L, "alice"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(new UserCreateRequest("alice", "password123", null, null, null)));

        assertEquals(AuthErrorCode.USERNAME_EXISTS, error.getErrorCode());
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void createStoresBcryptHashAndDefaultsNickname() {
        when(userMapper.selectOne(any())).thenReturn(null);

        service.create(new UserCreateRequest("alice", "password123", null, " a@x.com ", null));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        SysUser saved = captor.getValue();
        assertEquals("alice", saved.getUsername());
        assertEquals("alice", saved.getNickname());
        assertEquals(1, saved.getStatus());
        assertNotEquals("password123", saved.getPasswordHash());
        assertTrue(saved.getPasswordHash().startsWith("$2"));
    }

    @Test
    void updateRejectsMissingUser() {
        when(userMapper.selectById(99L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(99L, new UserUpdateRequest("nick", null, null)));

        assertEquals(ErrorCode.NOT_FOUND, error.getErrorCode());
    }

    @Test
    void updateStatusRejectsDisablingSelf() {
        when(userMapper.selectById(7L)).thenReturn(user(7L, "alice"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateStatus(7L, 7L, new UserStatusRequest(0)));

        assertEquals(AuthErrorCode.USER_CANNOT_DISABLE_SELF, error.getErrorCode());
        verify(refreshTokenMapper, never()).update(any(), any());
    }

    @Test
    void disableRevokesAllRefreshTokens() {
        when(userMapper.selectById(8L)).thenReturn(user(8L, "bob"));

        service.updateStatus(7L, 8L, new UserStatusRequest(0));

        verify(refreshTokenMapper).update(any(), any());
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
    }

    @Test
    void resetPasswordEncodesAndRevokesTokens() {
        when(userMapper.selectById(8L)).thenReturn(user(8L, "bob"));

        service.resetPassword(8L, new UserPasswordRequest("newpassword123"));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        assertNotEquals("newpassword123", captor.getValue().getPasswordHash());
        assertTrue(captor.getValue().getPasswordHash().startsWith("$2"));
        verify(refreshTokenMapper).update(any(), any());
    }

    private static SysUser user(Long id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(username);
        user.setStatus(1);
        return user;
    }
}
