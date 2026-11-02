package org.example.finflowbackend.reconciliation.task;

import org.example.finflowbackend.reconciliation.dto.MatchSummaryResponse;
import org.example.finflowbackend.reconciliation.entity.ReconciliationTask;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationTaskMapper;
import org.example.finflowbackend.reconciliation.service.ReconciliationMatchService;
import org.example.finflowbackend.system.api.ConfigApi;
import org.example.finflowbackend.system.api.ConfigKeys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 夜间对账任务单测（M17）：未到配置时间不跑、当日只跑一次（Redis 占位）、
 * 逐个匹配已完成未匹配任务、单个失败不中断整批、坏配置跳过不阻塞。
 */
class ReconciliationMatchTaskTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ReconciliationTaskMapper taskMapper = mock(ReconciliationTaskMapper.class);
    private final ReconciliationMatchService matchService = mock(ReconciliationMatchService.class);
    private final ConfigApi configApi = mock(ConfigApi.class);
    private final ReconciliationMatchTask task =
            new ReconciliationMatchTask(taskMapper, matchService, configApi, redisTemplate);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(configApi.getString(ConfigKeys.RECONCILIATION_MATCH_TIME)).thenReturn("02:00");
    }

    @Test
    void skipsBeforeConfiguredTime() {
        task.scanAndRun(LocalTime.of(1, 59));

        verify(taskMapper, never()).selectList(any());
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    /** 到达配置时间后当日只跑一次：第二次扫描被 Redis 占位挡住。 */
    @Test
    void runsOncePerDayAfterConfiguredTime() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true, false);
        when(taskMapper.selectList(any())).thenReturn(List.of(task(1L, "R1"), task(2L, "R2")));
        when(matchService.match(1L)).thenReturn(summary(1L, "R1"));
        when(matchService.match(2L)).thenReturn(summary(2L, "R2"));

        task.scanAndRun(LocalTime.of(2, 0));
        task.scanAndRun(LocalTime.of(2, 1));

        verify(matchService, times(1)).match(eq(1L));
        verify(matchService, times(1)).match(eq(2L));
        verify(valueOps, times(2)).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    /** 单任务失败只记日志：后续任务继续匹配，本轮返回成功数。 */
    @Test
    void continuesWithNextTaskWhenOneFails() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(taskMapper.selectList(any())).thenReturn(List.of(task(1L, "R1"), task(2L, "R2"), task(3L, "R3")));
        when(matchService.match(1L)).thenThrow(new IllegalStateException("row locked"));
        when(matchService.match(2L)).thenReturn(summary(2L, "R2"));
        when(matchService.match(3L)).thenReturn(summary(3L, "R3"));

        int matched = task.scanAndRun(LocalTime.of(2, 0));

        verify(matchService, times(1)).match(eq(1L));
        verify(matchService, times(1)).match(eq(2L));
        verify(matchService, times(1)).match(eq(3L));
        Assertions.assertEquals(2, matched);
    }

    /** 无可匹配任务时不调用匹配服务，占位仍保留（当日不再重复扫库）。 */
    @Test
    void skipsWhenNoPendingTask() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(taskMapper.selectList(any())).thenReturn(List.of());

        int matched = task.scanAndRun(LocalTime.of(2, 0));

        verify(matchService, never()).match(any());
        Assertions.assertEquals(0, matched);
    }

    /** 配置坏值跳过本轮，不抛异常阻塞扫描。 */
    @Test
    void skipsOnMalformedConfiguredTime() {
        when(configApi.getString(ConfigKeys.RECONCILIATION_MATCH_TIME)).thenReturn("2点");

        task.scanAndRun(LocalTime.of(2, 30));

        verify(taskMapper, never()).selectList(any());
    }

    private ReconciliationTask task(long id, String taskNo) {
        ReconciliationTask t = new ReconciliationTask();
        t.setId(id);
        t.setTaskNo(taskNo);
        return t;
    }

    private MatchSummaryResponse summary(long taskId, String taskNo) {
        return new MatchSummaryResponse(taskId, taskNo, 0, 0, 0, 0, 0, 0, LocalDateTime.now());
    }
}
