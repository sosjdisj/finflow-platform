package org.example.finflowbackend.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.auth.entity.SysRefreshToken;

/**
 * 刷新令牌 Mapper：简单 CRUD 走 BaseMapper。
 */
@Mapper
public interface SysRefreshTokenMapper extends BaseMapper<SysRefreshToken> {
}
