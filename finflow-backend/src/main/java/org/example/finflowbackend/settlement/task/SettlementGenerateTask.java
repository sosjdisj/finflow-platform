package org.example.finflowbackend.settlement.task;

import org.example.finflowbackend.settlement.service.SettlementGenerateService;
import org.example.finflowbackend.system.config.ConfigKeys;
import org.example.finflowbackend.system.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 结算单定时任务（S24 引入，S37 配置化）：每日到达配置的生成时间（settlement.generate-time，HH:mm）
 * 汇总昨日已清分记录生成结算单。每分钟扫描比对时间，替代原 @Scheduled cron（运营可读，改配置次日生效）。
 * 当日已跑标记走 Redis 原子占位（防多实例重复触发）；生成本身另有按日锁 + 唯一键双保险。
 * 测试直调 SettlementGenerateService.generateFor(date) 或 scanAndRun，不走定时器。
 */
@Component
public class SettlementGenerateTask {

    private static final Logger log = LoggerFactory.getLogger(SettlementGenerateTask.class);

    /** 当日已跑标记 key 前缀，值无意义，TTL 覆盖跨天后自动清理。 */
    static final String DONE_KEY_PREFIX = "finflow:settle:task:done:";
    private static final Duration DONE_TTL = Duration.ofHours(25);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final SettlementGenerateService generateService;
    private final ConfigService configService;
    private final StringRedisTemplate redisTemplate;

    public SettlementGenerateTask(SettlementGenerateService generateService,
                                  ConfigService configService, StringRedisTemplate redisTemplate) {
        this.generateService = generateService;
        this.configService = configService;
        this.redisTemplate = redisTemplate;
    }

    /** 每分钟扫描：到达配置时间且当日未跑 → 原子占位后执行。 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public void scan() {
        scanAndRun(LocalTime.now());
    }

    /** 扫描逻辑（公开便于测试直调）：占位成功才执行，执行失败撤占位，下一分钟重试。 */
    public void scanAndRun(LocalTime now) {
        LocalTime target = resolveTargetTime();
        if (target == null || now.isBefore(target)) {
            return;
        }
        String doneKey = DONE_KEY_PREFIX + LocalDate.now();
        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(doneKey, "1", DONE_TTL);
        if (!Boolean.TRUE.equals(claimed)) {
            return;
        }
        LocalDate yesterday = LocalDate.now().minusDays(1);
        try {
            log.info("[结算任务] 触发: settleDate={}, generateTime={}", yesterday, target);
            int count = generateService.generateFor(yesterday);
            log.info("[结算任务] 结束: settleDate={}, 结算单数={}", yesterday, count);
        } catch (RuntimeException e) {
            // 撤占位：失败后下一分钟自动重试，不留当天缺口
            redisTemplate.delete(doneKey);
            throw e;
        }
    }

    /** 解析配置的生成时间（HH:mm）；格式坏值记日志返回 null（跳过本轮，不阻塞扫描）。 */
    private LocalTime resolveTargetTime() {
        String raw = configService.getString(ConfigKeys.SETTLEMENT_GENERATE_TIME);
        try {
            return LocalTime.parse(raw, HH_MM);
        } catch (DateTimeParseException e) {
            log.warn("[结算任务] 生成时间配置不合法（应为 HH:mm）: {}", raw);
            return null;
        }
    }
}
