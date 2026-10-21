package org.example.finflowbackend.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.auth.entity.SysLoginLog;

/**
 * 登录日志 Mapper：只增不改，简单 CRUD 走 BaseMapper。
 */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {
}
