package org.example.finflowbackend.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.risk.entity.RiskRule;

/**
 * 风控规则 Mapper：纯 CRUD + 按优先级取启用规则，BaseMapper 足够，无需手写 SQL。
 */
@Mapper
public interface RiskRuleMapper extends BaseMapper<RiskRule> {
}
