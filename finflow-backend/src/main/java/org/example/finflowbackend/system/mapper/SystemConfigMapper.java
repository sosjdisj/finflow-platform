package org.example.finflowbackend.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.system.entity.SystemConfig;

/** 系统配置 Mapper：简单 CRUD 走 BaseMapper 即可。 */
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {
}
