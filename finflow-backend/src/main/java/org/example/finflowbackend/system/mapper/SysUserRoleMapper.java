package org.example.finflowbackend.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.system.entity.SysUserRole;

/**
 * 用户-角色关联 Mapper。简单增删查走 BaseMapper。
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
