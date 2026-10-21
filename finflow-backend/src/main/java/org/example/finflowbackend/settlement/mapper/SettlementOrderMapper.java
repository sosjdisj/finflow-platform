package org.example.finflowbackend.settlement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.settlement.entity.SettlementOrder;

@Mapper
public interface SettlementOrderMapper extends BaseMapper<SettlementOrder> {

    /** S25 状态推进：行级锁当前读，保证审核与执行并发时读到最新已提交状态。 */
    SettlementOrder selectForUpdate(Long id);
}
