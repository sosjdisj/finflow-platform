package org.example.finflowbackend.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.risk.entity.RiskBlacklist;

/**
 * 黑名单 Mapper：纯 CRUD，BaseMapper 足够，无需手写 SQL。
 */
@Mapper
public interface RiskBlacklistMapper extends BaseMapper<RiskBlacklist> {
}
