package org.example.finflowbackend.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.system.entity.SysMenu;

/**
 * 菜单 Mapper。简单 CRUD 走 BaseMapper。
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
}
