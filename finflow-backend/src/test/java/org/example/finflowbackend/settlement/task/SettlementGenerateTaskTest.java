package org.example.finflowbackend.settlement.task;

import org.example.finflowbackend.settlement.service.SettlementGenerateService;
import org.example.finflowbackend.system.config.ConfigKeys;
import org.example.finflowbackend.system.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结算定时任务单测（S37）：未到配置时间不跑、到达后跑且当日只跑一次（Redis 占位）、
 * 执行失败撤占位下一分钟可重试、坏配置跳过不阻塞。
 */
class SettlementGenerateTaskTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final SettlementGenerateService generateService = mock(SettlementGenerateService.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final SettlementGenerateTask task =
            new SettlementGenerateTask(generateService, configService, redisTemplate);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(configService.getString(ConfigKeys.SETTLEMENT_GENERATE_TIME)).thenReturn("01:00");
    }

    @Test
    void skipsBeforeConfiguredTime() {
        task.scanAndRun(LocalTime.of(0, 59));

        verify(generateService, never()).generateFor(any());
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    /** 到达配置时间执行一次；同日重复扫描被占位挡住，不重复生成。 */
    @Test
    void runsOncePerDayAfterConfiguredTime() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true, false);

        task.scanAndRun(LocalTime.of(1, 0));
        task.scanAndRun(LocalTime.of(1, 1));

        verify(generateService, times(1)).generateFor(eq(LocalDate.now().minusDays(1)));
        verify(valueOps, times(2)).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    /** 执行失败撤占位：下一分钟扫描可重试（重试成功）。 */
    @Test
    void releasesClaimWhenGenerationFails() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true, true);
        // 第一次失败（撤占位），第二次重试成功
        doThrow(new IllegalStateException("db down")).doReturn(1)
                .when(generateService).generateFor(any());

        try {
            task.scanAndRun(LocalTime.of(1, 0));
        } catch (IllegalStateException expected) {
            // 异常上抛由调度器记录
        }
        task.scanAndRun(LocalTime.of(1, 1));

        verify(generateService, times(2)).generateFor(any());
        verify(redisTemplate).delete(SettlementGenerateTask.DONE_KEY_PREFIX + LocalDate.now());
    }

    /** 配置坏值跳过本轮，不抛异常阻塞扫描。 */
    @Test
    void skipsOnMalformedConfiguredTime() {
        when(configService.getString(ConfigKeys.SETTLEMENT_GENERATE_TIME)).thenReturn("25:99");

        task.scanAndRun(LocalTime.of(1, 0));

        verify(generateService, never()).generateFor(any());
    }
}
