package org.example.finflowbackend.reconciliation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 对账批量匹配 SQL（S28）：手写 XML，利用 JOIN 与窗口函数一次产出五类结果。
 */
@Mapper
public interface ReconciliationMatchMapper {

    /** 清空任务已有匹配结果（重跑幂等的前提）。 */
    int deleteByTaskId(@Param("taskId") Long taskId);

    /**
     * 批量匹配并写入 reconciliation_record。
     * 口径：账单行按商户单号关联支付单 → 比金额 → 比状态；
     * REFUNDED 行比对支付单名下成功退款单金额合计；平台当日成功支付单不在账单中记 MISSING_IN_CHANNEL。
     */
    int insertMatchResults(@Param("taskId") Long taskId,
                           @Param("billDate") LocalDate billDate,
                           @Param("channelCode") String channelCode);
}
