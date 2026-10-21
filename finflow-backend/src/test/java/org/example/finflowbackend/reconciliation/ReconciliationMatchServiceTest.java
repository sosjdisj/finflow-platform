package org.example.finflowbackend.reconciliation;

import org.example.finflowbackend.common.event.DomainEvent;
import org.example.finflowbackend.common.event.DomainEventPublisher;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.reconciliation.dto.MatchSummaryResponse;
import org.example.finflowbackend.reconciliation.entity.ReconciliationRecord;
import org.example.finflowbackend.reconciliation.entity.ReconciliationTask;
import org.example.finflowbackend.reconciliation.event.ReconciliationAbnormalPayload;
import org.example.finflowbackend.reconciliation.event.ReconciliationTopics;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationMatchMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationRecordMapper;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationTaskMapper;
import org.example.finflowbackend.reconciliation.service.ReconciliationMatchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对账匹配编排单测：任务不存在/状态不允许拒绝，成功路径清旧 → 批量插入 → 汇总回写；
 * 发现差异按类型汇总发布对账异常事件、全平不发（S33 通知源）。
 * 匹配 SQL 的结果正确性由集成测试覆盖。
 */
class ReconciliationMatchServiceTest {

    private final ReconciliationTaskMapper taskMapper = mock(ReconciliationTaskMapper.class);
    private final ReconciliationRecordMapper recordMapper = mock(ReconciliationRecordMapper.class);
    private final ReconciliationMatchMapper matchMapper = mock(ReconciliationMatchMapper.class);
    private final DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);

    private ReconciliationMatchService newService() {
        return new ReconciliationMatchService(taskMapper, recordMapper, matchMapper, eventPublisher);
    }

    private ReconciliationTask task(String status) {
        ReconciliationTask task = new ReconciliationTask();
        task.setId(100L);
        task.setTaskNo("R20261018000001");
        task.setChannelCode("MOCK_WALLET");
        task.setBillDate(LocalDate.of(2026, 10, 17));
        task.setStatus(status);
        return task;
    }

    @Test
    void matchRejectsUnknownTask() {
        when(taskMapper.selectForUpdate(100L)).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class,
                () -> newService().match(100L));
        assertEquals(ReconciliationErrorCode.TASK_NOT_FOUND, error.getErrorCode());
        verify(matchMapper, never()).deleteByTaskId(any());
    }

    @Test
    void matchRejectsTaskNotCompleted() {
        when(taskMapper.selectForUpdate(100L)).thenReturn(task("PARSING"));
        BusinessException error = assertThrows(BusinessException.class,
                () -> newService().match(100L));
        assertEquals(ReconciliationErrorCode.TASK_NOT_MATCHABLE, error.getErrorCode());
        verify(matchMapper, never()).deleteByTaskId(any());
    }

    @Test
    void matchClearsInsertsAndSummarizes() {
        ReconciliationTask task = task("COMPLETED");
        when(taskMapper.selectForUpdate(100L)).thenReturn(task);
        // count 调用顺序：MATCHED / AMOUNT / STATUS / MISSING_IN_PLATFORM / MISSING_IN_CHANNEL
        when(recordMapper.selectCount(any())).thenReturn(3L, 2L, 2L, 1L, 1L);
        // 差异记录（事件发布源）：金额不平 2 笔、状态不平 2 笔、平台缺单 1 笔、渠道缺单 1 笔
        when(recordMapper.selectList(any())).thenReturn(List.of(
                record(ReconciliationRecord.AMOUNT_MISMATCH, 10000L, 9900L),
                record(ReconciliationRecord.AMOUNT_MISMATCH, 20000L, 20000L),
                record(ReconciliationRecord.STATUS_MISMATCH, 5000L, 5000L),
                record(ReconciliationRecord.MISSING_IN_PLATFORM, null, 7000L),
                record(ReconciliationRecord.MISSING_IN_CHANNEL, 8000L, null)));

        MatchSummaryResponse summary = newService().match(100L);

        // 先清旧再批量匹配，参数取自任务
        verify(matchMapper).deleteByTaskId(100L);
        verify(matchMapper).insertMatchResults(100L, LocalDate.of(2026, 10, 17), "MOCK_WALLET");

        // 汇总回写任务
        ArgumentCaptor<ReconciliationTask> captor = ArgumentCaptor.forClass(ReconciliationTask.class);
        verify(taskMapper).updateById(captor.capture());
        ReconciliationTask updated = captor.getValue();
        assertEquals(3, updated.getMatchedCount());
        assertEquals(2, updated.getAmountMismatchCount());
        assertEquals(2, updated.getStatusMismatchCount());
        assertEquals(1, updated.getMissingInPlatformCount());
        assertEquals(1, updated.getMissingInChannelCount());
        assertNotNull(updated.getMatchedAt());

        // 响应与汇总一致
        assertEquals(3, summary.matchedCount());
        assertEquals(2, summary.amountMismatchCount());
        assertEquals(2, summary.statusMismatchCount());
        assertEquals(1, summary.missingInPlatformCount());
        assertEquals(1, summary.missingInChannelCount());
        assertEquals(9, summary.totalRecords());
        assertNotNull(summary.matchedAt());
        assertEquals("R20261018000001", summary.taskNo());
    }

    /** S33 通知源：发现差异按类型汇总发布（每类一条，金额为平台/渠道侧合计），全平不发。 */
    @Test
    void publishesAbnormalEventPerDifferenceType() {
        ReconciliationTask task = task("COMPLETED");
        when(taskMapper.selectForUpdate(100L)).thenReturn(task);
        when(recordMapper.selectCount(any())).thenReturn(0L, 2L, 0L, 0L, 0L);
        when(recordMapper.selectList(any())).thenReturn(List.of(
                record(ReconciliationRecord.AMOUNT_MISMATCH, 10000L, 9900L),
                record(ReconciliationRecord.AMOUNT_MISMATCH, 20000L, 20000L)));

        newService().match(100L);

        ArgumentCaptor<DomainEvent> events = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, times(1)).publish(eq(ReconciliationTopics.TOPIC_ABNORMAL),
                eq("100"), events.capture());
        DomainEvent event = events.getValue();
        assertEquals(ReconciliationTopics.TYPE_RECONCILIATION_DIFFERENCE, event.eventType());
        ReconciliationAbnormalPayload payload = new ObjectMapper().convertValue(
                event.payload(), ReconciliationAbnormalPayload.class);
        assertEquals(100L, payload.taskId());
        assertEquals("R20261018000001", payload.taskNo());
        assertEquals(LocalDate.of(2026, 10, 17), payload.billDate());
        assertEquals("AMOUNT_MISMATCH", payload.differenceType());
        assertEquals(30000L, payload.platformAmount());
        assertEquals(29900L, payload.channelAmount());
        assertEquals(2, payload.count());
    }

    @Test
    void allMatchedPublishesNothing() {
        ReconciliationTask task = task("COMPLETED");
        when(taskMapper.selectForUpdate(100L)).thenReturn(task);
        when(recordMapper.selectCount(any())).thenReturn(3L, 0L, 0L, 0L, 0L);
        when(recordMapper.selectList(any())).thenReturn(List.of());

        newService().match(100L);

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    private ReconciliationRecord record(String result, Long platformAmount, Long billAmount) {
        ReconciliationRecord record = new ReconciliationRecord();
        record.setTaskId(100L);
        record.setResult(result);
        record.setPlatformAmount(platformAmount);
        record.setBillAmount(billAmount);
        return record;
    }
}
