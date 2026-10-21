package org.example.finflowbackend.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.system.entity.SysPermission;

import java.util.List;

/**
 * 权限点 Mapper。按用户联查权限编码与全量查询走 XML（三表联查不硬凑 Wrapper）。
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /** 查用户拥有的启用权限编码（经角色关联）。 */
    List<String> selectPermissionCodesByUserId(Long userId);

    /** 查全部启用权限编码（供内置超级管理员授权使用）。 */
    List<String> selectAllEnabledCodes();
}
