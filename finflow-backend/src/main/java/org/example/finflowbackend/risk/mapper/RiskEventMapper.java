package org.example.finflowbackend.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.risk.entity.RiskEvent;

/**
 * 风险事件 Mapper：只增不改，BaseMapper 足够，无需手写 SQL。
 */
@Mapper
public interface RiskEventMapper extends BaseMapper<RiskEvent> {
}
