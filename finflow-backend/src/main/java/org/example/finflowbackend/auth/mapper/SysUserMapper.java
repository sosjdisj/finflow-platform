package org.example.finflowbackend.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.auth.entity.SysUser;

/**
 * 用户表 Mapper：全部是简单 CRUD，走 BaseMapper，不手写 XML。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
