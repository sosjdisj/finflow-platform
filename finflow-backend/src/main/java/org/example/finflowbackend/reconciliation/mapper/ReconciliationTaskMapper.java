package org.example.finflowbackend.reconciliation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.finflowbackend.reconciliation.entity.ReconciliationTask;

@Mapper
public interface ReconciliationTaskMapper extends BaseMapper<ReconciliationTask> {

    /** S28 匹配互斥：行级锁当前读，并发匹配同一任务时串行化。 */
    ReconciliationTask selectForUpdate(@Param("id") Long id);
}
