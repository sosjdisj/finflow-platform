package org.example.finflowbackend.settlement.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.example.finflowbackend.clearing.entity.ClearingOrder;
import org.example.finflowbackend.common.api.ErrorCode;
import org.example.finflowbackend.common.exception.BusinessException;
import org.example.finflowbackend.settlement.SettlementErrorCode;
import org.example.finflowbackend.settlement.SettlementStatus;
import org.example.finflowbackend.settlement.entity.SettlementDetail;
import org.example.finflowbackend.settlement.entity.SettlementOrder;
import org.example.finflowbackend.settlement.mapper.SettlementDetailMapper;
import org.example.finflowbackend.settlement.mapper.SettlementOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 单商户结算单落库（S24）。独立组件：generateOne 须经代理调用才有事务边界
 * （同类内部调用 @Transactional 不生效，S16 踩坑）。
 * 单事务：插 PENDING → 逐笔明细 → 汇总校验 → CAS 推 CALCULATED。
 */
@Component
public class SettlementOrderWriter {

    private static final Logger log = LoggerFactory.getLogger(SettlementOrderWriter.class);
    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SettlementOrderMapper settlementOrderMapper;
    private final SettlementDetailMapper settlementDetailMapper;

    public SettlementOrderWriter(SettlementOrderMapper settlementOrderMapper,
                                 SettlementDetailMapper settlementDetailMapper) {
        this.settlementOrderMapper = settlementOrderMapper;
        this.settlementDetailMapper = settlementDetailMapper;
    }

    /**
     * 生成一商户一日的结算单。已存在（uk 冲突 / 预查命中）返回 false，其余异常上抛回滚。
     */
    @Transactional
    public boolean generateOne(Long merchantId, LocalDate settleDate, List<ClearingOrder> clearings) {
        // 部分生成场景（上次中途失败）：已有结算单则跳过，uk 是最终防线
        if (settlementOrderMapper.selectCount(Wrappers.<SettlementOrder>query()
                .eq("merchant_id", merchantId).eq("settle_date", settleDate)) > 0) {
            return false;
        }
        long total = clearings.stream().mapToLong(ClearingOrder::getMerchantIncome).sum();

        SettlementOrder order = new SettlementOrder();
        order.setSettleNo(nextSettleNo(settleDate));
        order.setMerchantId(merchantId);
        order.setSettleDate(settleDate);
        order.setOrderCount(clearings.size());
        order.setTotalAmount(total);
        order.setCurrency(clearings.getFirst().getCurrency());
        order.setStatus(SettlementStatus.PENDING.name());
        try {
            settlementOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            log.info("[结算生成] 结算单已存在，跳过: merchantId={}, settleDate={}", merchantId, settleDate);
            return false;
        }
        long detailSum = 0L;
        for (ClearingOrder clearing : clearings) {
            SettlementDetail detail = new SettlementDetail();
            detail.setSettlementId(order.getId());
            detail.setClearingId(clearing.getId());
            detail.setOrderId(clearing.getOrderId());
            detail.setPayAmount(clearing.getPayAmount());
            detail.setMerchantIncome(clearing.getMerchantIncome());
            settlementDetailMapper.insert(detail);
            detailSum += clearing.getMerchantIncome();
        }
        // 汇总校验：明细之和必须等于结算单总额（防复制遗漏，错则整单回滚）
        if (detailSum != total) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "结算明细汇总与结算单总额不一致: settleNo=" + order.getSettleNo());
        }
        // CAS 推进 PENDING → CALCULATED（同事务，状态机校验前置）
        SettlementStatus.MACHINE.checkOrThrow(SettlementStatus.PENDING, SettlementStatus.CALCULATED,
                SettlementErrorCode.ILLEGAL_STATUS_TRANSITION);
        int updated = settlementOrderMapper.update(null, Wrappers.<SettlementOrder>update()
                .set("status", SettlementStatus.CALCULATED.name())
                .set("calculated_at", LocalDateTime.now())
                .eq("id", order.getId())
                .eq("status", SettlementStatus.PENDING.name()));
        if (updated != 1) {
            throw new BusinessException(SettlementErrorCode.ILLEGAL_STATUS_TRANSITION,
                    "结算单状态推进失败: settleNo=" + order.getSettleNo());
        }
        return true;
    }

    /** 结算单号：ST + 结算日 + 6 位随机，uk 兜底，碰撞重试。 */
    private String nextSettleNo(LocalDate settleDate) {
        String date = settleDate.format(NO_DATE);
        for (int i = 0; i < 3; i++) {
            String no = "ST" + date + (100000 + RANDOM.nextInt(900000));
            if (settlementOrderMapper.selectCount(
                    Wrappers.<SettlementOrder>query().eq("settle_no", no)) == 0) {
                return no;
            }
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "结算单号生成失败，请重试");
    }
}
