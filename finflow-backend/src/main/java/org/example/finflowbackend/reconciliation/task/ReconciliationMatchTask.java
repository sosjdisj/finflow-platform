package org.example.finflowbackend.reconciliation.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.reconciliation.ReconciliationStatus;
import org.example.finflowbackend.reconciliation.dto.MatchSummaryResponse;
import org.example.finflowbackend.reconciliation.entity.ReconciliationTask;
import org.example.finflowbackend.reconciliation.mapper.ReconciliationTaskMapper;
import org.example.finflowbackend.reconciliation.service.ReconciliationMatchService;
import org.example.finflowbackend.system.api.ConfigApi;
import org.example.finflowbackend.system.api.ConfigKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 对账夜间自动匹配任务（M17）：每日到达配置时间（reconciliation.match-time，HH:mm）后，
 * 把当日上传、已完成解析但尚未匹配（matched_at 为空）的账单任务逐个匹配。
 *
 * <p>这是拆出批处理服务的可验证载体：{@code finflow.batch.worker=true} 时本任务在执行进程注册，
 * backend 拆出后置 false，夜间负载只落在 finflow-batch 进程；切回 true 即可回退到单体进程。</p>
 *
 * <p>当日已跑标记走 Redis 原子占位（防多实例重复触发）；单个任务失败只记日志不中断整批，
 * 该任务 matched_at 仍为空，次日（或手工重跑）自动补上。</p>
 */
@Component
@ConditionalOnProperty(name = "finflow.batch.worker", havingValue = "true", matchIfMissing = true)
public class ReconciliationMatchTask {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationMatchTask.class);

    /** 当日已跑标记 key 前缀，TTL 覆盖跨天后自动清理。 */
    static final String DONE_KEY_PREFIX = "finflow:recon:task:done:";
    private static final Duration DONE_TTL = Duration.ofHours(25);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final ReconciliationTaskMapper taskMapper;
    private final ReconciliationMatchService matchService;
    private final ConfigApi configApi;
    private final StringRedisTemplate redisTemplate;

    public ReconciliationMatchTask(ReconciliationTaskMapper taskMapper,
                                   ReconciliationMatchService matchService,
                                   ConfigApi configApi,
                                   StringRedisTemplate redisTemplate) {
        this.taskMapper = taskMapper;
        this.matchService = matchService;
        this.configApi = configApi;
        this.redisTemplate = redisTemplate;
    }

    /** 每分钟扫描：到达配置时间且当日未跑 → 原子占位后执行。 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void scan() {
        scanAndRun(LocalTime.now());
    }

    /**
     * 扫描逻辑（公开便于测试直调）：占位成功才执行；逐任务匹配，单条失败只记日志。
     * 返回本轮匹配的任务数（测试与日志共用）。
     */
    public int scanAndRun(LocalTime now) {
        LocalTime target = resolveTargetTime();
        if (target == null || now.isBefore(target)) {
            return 0;
        }
        String doneKey = DONE_KEY_PREFIX + LocalDate.now();
        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(doneKey, "1", DONE_TTL);
        if (!Boolean.TRUE.equals(claimed)) {
            return 0;
        }
        List<ReconciliationTask> tasks = taskMapper.selectList(Wrappers.<ReconciliationTask>query()
                .eq("status", ReconciliationStatus.COMPLETED.name())
                .isNull("matched_at")
                .orderByAsc("id"));
        if (tasks.isEmpty()) {
            log.info("[对账任务] 触发: 无待匹配任务, matchTime={}", target);
            return 0;
        }
        int matched = 0;
        for (ReconciliationTask task : tasks) {
            try {
                MatchSummaryResponse summary = matchService.match(task.getId());
                matched++;
                log.info("[对账任务] 匹配完成: taskNo={}, 一致={}, 差异={}",
                        task.getTaskNo(), summary.matchedCount(),
                        summary.totalRecords() - summary.matchedCount());
            } catch (RuntimeException e) {
                log.warn("[对账任务] 匹配失败，留待下次重试: taskNo={}", task.getTaskNo(), e);
            }
        }
        log.info("[对账任务] 结束: 待匹配={}, 成功={}", tasks.size(), matched);
        return matched;
    }

    /** 解析配置的匹配时间（HH:mm）；格式坏值记日志返回 null（跳过本轮，不阻塞扫描）。 */
    private LocalTime resolveTargetTime() {
        String raw = configApi.getString(ConfigKeys.RECONCILIATION_MATCH_TIME);
        try {
            return LocalTime.parse(raw, HH_MM);
        } catch (DateTimeParseException e) {
            log.warn("[对账任务] 匹配时间配置不合法（应为 HH:mm）: {}", raw);
            return null;
        }
    }
}
