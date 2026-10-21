package org.example.finflowbackend.settlement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.settlement.entity.SettlementOrderStateLog;

@Mapper
public interface SettlementOrderStateLogMapper extends BaseMapper<SettlementOrderStateLog> {
}
