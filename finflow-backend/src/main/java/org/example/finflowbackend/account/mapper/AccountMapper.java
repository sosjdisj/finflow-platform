package org.example.finflowbackend.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.finflowbackend.account.entity.Account;

/**
 * 账户 Mapper。行锁读走手写 XML，其余简单 CRUD 用 BaseMapper。
 */
@Mapper
public interface AccountMapper extends BaseMapper<Account> {

    /** 按 ID 行锁读（select ... for update），资金变更前必须先走这里。 */
    Account selectForUpdate(@Param("id") Long id);
}
