package org.example.finflowbackend.settlement.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.clearing.entity.ClearingOrder;
import org.example.finflowbackend.clearing.mapper.ClearingOrderMapper;
import org.example.finflowbackend.common.lock.RedisLockService;
import org.example.finflowbackend.settlement.entity.SettlementOrder;
import org.example.finflowbackend.settlement.mapper.SettlementOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 结算单生成编排（S24）：汇总某结算日的已清分记录，按商户生成结算单。
 * T+1 语义——定时任务传昨日；补数场景可传历史日期。
 * 幂等三道防线：Redis 按日锁（防多实例并发）→ uk(merchant_id, settle_date)
 * （防重复生成）→ uk(clearing_id)（防一笔清分重复入账）。
 * settle_cycle（T1/D1）暂不参与判断——按报告口径统一汇总，差异留给后续。
 * 单商户落库事务见 {@link SettlementOrderWriter}；不做审核与打款（S25）。
 */
@Service
public class SettlementGenerateService {

    private static final Logger log = LoggerFactory.getLogger(SettlementGenerateService.class);

    /** 按日锁 key 前缀；TTL 兜底实例崩溃（任务本身秒级完成）。 */
    static final String LOCK_KEY_PREFIX = "finflow:settle:generate:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final ClearingOrderMapper clearingOrderMapper;
    private final SettlementOrderWriter orderWriter;
    private final RedisLockService redisLockService;

    public SettlementGenerateService(ClearingOrderMapper clearingOrderMapper,
                                     SettlementOrderWriter orderWriter,
                                     RedisLockService redisLockService) {
        this.clearingOrderMapper = clearingOrderMapper;
        this.orderWriter = orderWriter;
        this.redisLockService = redisLockService;
    }

    /**
     * 生成某结算日的结算单（可重入，已生成的商户幂等跳过）。
     *
     * @return 本次新生成的结算单数；锁被他人持有时返回 0
     */
    public int generateFor(LocalDate settleDate) {
        String lockKey = LOCK_KEY_PREFIX + settleDate;
        Integer generated = redisLockService.tryRun(lockKey, LOCK_TTL,
                () -> doGenerate(settleDate),
                () -> log.info("[结算生成] 其他实例持有锁，本次跳过: settleDate={}", settleDate));
        return generated == null ? 0 : generated;
    }

    private Integer doGenerate(LocalDate settleDate) {
        List<ClearingOrder> cleared = clearingOrderMapper.selectList(
                Wrappers.<ClearingOrder>query()
                        .eq("settle_date", settleDate)
                        .eq("status", "CLEARED"));
        if (cleared.isEmpty()) {
            log.info("[结算生成] 该日无已清分记录: settleDate={}", settleDate);
            return 0;
        }
        Map<Long, List<ClearingOrder>> byMerchant = cleared.stream()
                .collect(Collectors.groupingBy(ClearingOrder::getMerchantId));
        int generated = 0;
        for (Map.Entry<Long, List<ClearingOrder>> entry : byMerchant.entrySet()) {
            if (orderWriter.generateOne(entry.getKey(), settleDate, entry.getValue())) {
                generated++;
            }
        }
        log.info("[结算生成] 完成: settleDate={}, 商户数={}, 新生成结算单={}",
                settleDate, byMerchant.size(), generated);
        return generated;
    }
}
