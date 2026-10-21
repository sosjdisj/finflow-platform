package org.example.finflowbackend.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.system.entity.SysRolePermission;

/**
 * 角色-权限关联 Mapper。简单增删查走 BaseMapper。
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {
}
