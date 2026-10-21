package org.example.finflowbackend.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.finflowbackend.ledger.entity.AccountLedger;

/**
 * 资金流水 Mapper。只增不改：仅 insert（BaseMapper）与查询，不暴露任何更新方法。
 */
@Mapper
public interface AccountLedgerMapper extends BaseMapper<AccountLedger> {

    /** 账户流水净额 Σamount（一致性校验用），聚合统计走手写 XML。 */
    long sumAmountByAccountId(@Param("accountId") Long accountId);
}
