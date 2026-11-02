package org.example.finflowbackend.reconciliation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.example.finflowbackend.reconciliation.dto.MissingInChannelCandidate;

import java.time.LocalDate;
import java.util.List;

/**
 * 对账批量匹配 SQL（S28）：手写 XML，利用 JOIN 与窗口函数一次产出账单侧结果；
 * 平台侧缺单（MISSING_IN_CHANNEL）因渠道留痕已迁 finflow_channel，改由 channel.api 判定后批量写入。
 */
@Mapper
public interface ReconciliationMatchMapper {

    /** 清空任务已有匹配结果（重跑幂等的前提）。 */
    int deleteByTaskId(@Param("taskId") Long taskId);

    /**
     * 账单侧批量匹配并写入 reconciliation_record（MATCHED / AMOUNT_MISMATCH / STATUS_MISMATCH / MISSING_IN_PLATFORM）。
     * 口径：账单行按商户单号关联支付单 → 比金额 → 比状态；REFUNDED 行比对支付单名下成功退款单金额合计。
     */
    int insertMatchResults(@Param("taskId") Long taskId);

    /**
     * 平台当日成功单（PAID/SETTLED，当日进 PAID，且未出现在账单中）的 MISSING_IN_CHANNEL 候选。
     * 不含渠道过滤：渠道归属由 {@code channel.api.PaymentTransactionApi} 判定，避免跨 schema 读渠道表。
     */
    List<MissingInChannelCandidate> selectMissingInChannelCandidates(@Param("taskId") Long taskId,
                                                                     @Param("billDate") LocalDate billDate);

    /** 批量写入 MISSING_IN_CHANNEL 结果（渠道判定通过且已回填渠道单号）。 */
    int insertMissingInChannelRecords(@Param("taskId") Long taskId,
                                      @Param("records") List<MissingInChannelCandidate> records);
}
