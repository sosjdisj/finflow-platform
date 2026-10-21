package org.example.finflowbackend.reconciliation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.reconciliation.entity.ReconciliationDifference;

/**
 * 差异单 Mapper：处理流为单条状态推进 + 调账同事务，简单 CRUD 用 BaseMapper 即可，无需手写 SQL。
 */
@Mapper
public interface ReconciliationDifferenceMapper extends BaseMapper<ReconciliationDifference> {
}
