package org.example.finflowbackend.reconciliation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.finflowbackend.reconciliation.entity.ReconciliationRecord;

@Mapper
public interface ReconciliationRecordMapper extends BaseMapper<ReconciliationRecord> {
}
