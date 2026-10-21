package org.example.finflowbackend.reconciliation.dto;

import java.time.LocalDateTime;

/**
 * 匹配执行结果：五类结果汇总数量（与任务表汇总列一致）。
 */
public record MatchSummaryResponse(
        Long taskId,
        String taskNo,
        long matchedCount,
        long amountMismatchCount,
        long statusMismatchCount,
        long missingInPlatformCount,
        long missingInChannelCount,
        long totalRecords,
        LocalDateTime matchedAt) {
}
