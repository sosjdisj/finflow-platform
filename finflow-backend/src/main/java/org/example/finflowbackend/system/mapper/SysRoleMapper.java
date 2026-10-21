package org.example.finflowbackend.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.system.entity.SysRole;

import java.util.List;

/**
 * 角色 Mapper。按用户联查角色编码走 XML（三表联查不硬凑 Wrapper）。
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /** 查用户拥有的启用角色编码。 */
    List<String> selectRoleCodesByUserId(Long userId);
}
