package org.example.finflowbackend.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.finflowbackend.auth.AuthErrorCode;
import org.example.finflowbackend.auth.dto.UserAdminResponse;
import org.example.finflowbackend.auth.dto.UserCreateRequest;
import org.example.finflowbackend.auth.dto.UserPasswordRequest;
import org.example.finflowbackend.auth.dto.UserStatusRequest;
import org.example.finflowbackend.auth.dto.UserUpdateRequest;
import org.example.finflowbackend.auth.entity.SysRefreshToken;
import org.example.finflowbackend.auth.entity.SysUser;
import org.example.finflowbackend.auth.mapper.SysRefreshTokenMapper;
import org.example.finflowbackend.auth.mapper.SysUserMapper;
import org.example.finflowbackend.auth.service.UserAdminService;
import org.example.finflowbackend.common.api.ErrorCode;
import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 用户管理实现。禁用/重置密码一律吊销该用户全部 refresh token，让旧会话立即失效。
 */
@Service
public class UserAdminServiceImpl implements UserAdminService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final SysUserMapper userMapper;
    private final SysRefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;

    public UserAdminServiceImpl(SysUserMapper userMapper, SysRefreshTokenMapper refreshTokenMapper,
                                PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PageResponse<UserAdminResponse> page(long page, long size, String keyword) {
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size),
                Wrappers.<SysUser>lambdaQuery()
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(SysUser::getUsername, keyword.trim())
                                .or().like(SysUser::getNickname, keyword.trim()))
                        .orderByDesc(SysUser::getCreatedAt));
        return PageResponse.of(result.convert(UserAdminResponse::from));
    }

    @Override
    public UserAdminResponse create(UserCreateRequest request) {
        String username = request.username().trim();
        if (userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username)) != null) {
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
            // 并发下唯一索引兜底
            throw new BusinessException(AuthErrorCode.USERNAME_EXISTS);
        }
        return UserAdminResponse.from(user);
    }

    @Override
    public UserAdminResponse update(Long id, UserUpdateRequest request) {
        SysUser user = requireUser(id);
        SysUser change = new SysUser();
        change.setId(id);
        change.setNickname(StringUtils.hasText(request.nickname()) ? request.nickname().trim() : user.getNickname());
        change.setEmail(trimToNull(request.email()));
        change.setPhone(trimToNull(request.phone()));
        userMapper.updateById(change);
        return UserAdminResponse.from(reload(id));
    }

    @Override
    @Transactional
    public UserAdminResponse updateStatus(Long operatorId, Long id, UserStatusRequest request) {
        requireUser(id);
        if (request.status() == STATUS_DISABLED && operatorId.equals(id)) {
            throw new BusinessException(AuthErrorCode.USER_CANNOT_DISABLE_SELF);
        }
        SysUser change = new SysUser();
        change.setId(id);
        change.setStatus(request.status());
        userMapper.updateById(change);
        if (request.status() == STATUS_DISABLED) {
            revokeAllTokens(id);
        }
        return UserAdminResponse.from(reload(id));
    }

    @Override
    @Transactional
    public void resetPassword(Long id, UserPasswordRequest request) {
        requireUser(id);
        SysUser change = new SysUser();
        change.setId(id);
        change.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(change);
        revokeAllTokens(id);
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return user;
    }

    private SysUser reload(Long id) {
        return userMapper.selectById(id);
    }

    /** 吊销该用户全部有效令牌（一次性 UPDATE，与 AuthServiceImpl 同语义）。 */
    private void revokeAllTokens(Long userId) {
        refreshTokenMapper.update(null, Wrappers.<SysRefreshToken>update()
                .eq("user_id", userId)
                .isNull("revoked_at")
                .set("revoked_at", LocalDateTime.now()));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
