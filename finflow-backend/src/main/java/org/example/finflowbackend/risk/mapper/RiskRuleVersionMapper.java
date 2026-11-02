package org.example.finflowbackend.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.risk.entity.RiskRuleVersion;

/**
 * 风控规则历史版本 Mapper（表 finflow_risk.risk_rule_version，只增不改）。
 */
@Mapper
public interface RiskRuleVersionMapper extends BaseMapper<RiskRuleVersion> {
}
