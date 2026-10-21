package org.example.finflowbackend.reconciliation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.common.event.DomainEventPublisher;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.reconciliation.ReconciliationErrorCode;
import org.example.finflowbackend.reconciliation.ReconciliationStatus;
import org.example.finflowbackend.reconciliation.dto.MatchSummaryResponse;
import org.example.finflowbackend.reconciliation.entity.ReconciliationRecord;
import org.example.finflowbackend.reconciliation.entity.ReconciliationTask;
import org.example.finflowbackend.reconciliation.event.ReconciliationAbnormalPayload;
import org.example.finflowbackend.reconciliation.event.ReconciliationTopics;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationMatchMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationRecordMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对账匹配（S28）：任务级批量匹配，一条事务内清旧结果 → 批量 SQL 产出五类记录 → 汇总回写任务。
 * 行锁串行化并发匹配；仅 COMPLETED 任务可匹配，可重跑覆盖。
 * 匹配完成发现差异时，按差异类型汇总发布对账异常事件（事务提交后发布，S33 通知源）。
 */
@Service
public class ReconciliationMatchService {

    private final ReconciliationTaskMapper taskMapper;
    private final ReconciliationRecordMapper recordMapper;
    private final ReconciliationMatchMapper matchMapper;
    private final DomainEventPublisher eventPublisher;

    public ReconciliationMatchService(ReconciliationTaskMapper taskMapper,
                                      ReconciliationRecordMapper recordMapper,
                                      ReconciliationMatchMapper matchMapper,
                                      DomainEventPublisher eventPublisher) {
        this.taskMapper = taskMapper;
        this.recordMapper = recordMapper;
        this.matchMapper = matchMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public MatchSummaryResponse match(Long taskId) {
        ReconciliationTask task = taskMapper.selectForUpdate(taskId);
        if (task == null) {
            throw new BusinessException(ReconciliationErrorCode.TASK_NOT_FOUND);
        }
        if (!ReconciliationStatus.COMPLETED.name().equals(task.getStatus())) {
            throw new BusinessException(ReconciliationErrorCode.TASK_NOT_MATCHABLE,
                    "当前状态 " + task.getStatus() + "，仅解析完成（COMPLETED）的任务可匹配");
        }

        matchMapper.deleteByTaskId(taskId);
        matchMapper.insertMatchResults(taskId, task.getBillDate(), task.getChannelCode());

        long matched = count(taskId, ReconciliationRecord.MATCHED);
        long amountMismatch = count(taskId, ReconciliationRecord.AMOUNT_MISMATCH);
        long statusMismatch = count(taskId, ReconciliationRecord.STATUS_MISMATCH);
        long missingInPlatform = count(taskId, ReconciliationRecord.MISSING_IN_PLATFORM);
        long missingInChannel = count(taskId, ReconciliationRecord.MISSING_IN_CHANNEL);

        task.setMatchedAt(LocalDateTime.now());
        task.setMatchedCount((int) matched);
        task.setAmountMismatchCount((int) amountMismatch);
        task.setStatusMismatchCount((int) statusMismatch);
        task.setMissingInPlatformCount((int) missingInPlatform);
        task.setMissingInChannelCount((int) missingInChannel);
        taskMapper.updateById(task);

        publishAbnormalEvents(task);

        return new MatchSummaryResponse(taskId, task.getTaskNo(),
                matched, amountMismatch, statusMismatch, missingInPlatform, missingInChannel,
                matched + amountMismatch + statusMismatch + missingInPlatform + missingInChannel,
                task.getMatchedAt());
    }

    private long count(Long taskId, String result) {
        Long c = recordMapper.selectCount(new LambdaQueryWrapper<ReconciliationRecord>()
                .eq(ReconciliationRecord::getTaskId, taskId)
                .eq(ReconciliationRecord::getResult, result));
        return c == null ? 0 : c;
    }

    /**
     * 对账异常事件（S33 通知源）：按差异类型汇总发布，每类一条（result → [笔数, 平台侧合计, 渠道侧合计]），
     * 无差异不发；@Transactional 内调用，DomainEventPublisher 注册 afterCommit 保证提交后才发。
     */
    private void publishAbnormalEvents(ReconciliationTask task) {
        List<ReconciliationRecord> mismatches = recordMapper.selectList(new LambdaQueryWrapper<ReconciliationRecord>()
                .eq(ReconciliationRecord::getTaskId, task.getId())
                .ne(ReconciliationRecord::getResult, ReconciliationRecord.MATCHED));
        Map<String, long[]> aggregated = new LinkedHashMap<>();
        for (ReconciliationRecord record : mismatches) {
            long[] agg = aggregated.computeIfAbsent(record.getResult(), k -> new long[3]);
            agg[0]++;
            agg[1] += record.getPlatformAmount() == null ? 0 : record.getPlatformAmount();
            agg[2] += record.getBillAmount() == null ? 0 : record.getBillAmount();
        }
        aggregated.forEach((result, agg) -> eventPublisher.publish(ReconciliationTopics.TOPIC_ABNORMAL,
                String.valueOf(task.getId()),
                DomainEvent.of(ReconciliationTopics.TYPE_RECONCILIATION_DIFFERENCE,
                        new ReconciliationAbnormalPayload(task.getId(), task.getTaskNo(), task.getBillDate(),
                                task.getChannelCode(), result, agg[1], agg[2], agg[0]))));
    }
}
