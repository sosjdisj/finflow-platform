package org.example.finflowbackend.clearing.service;

import org.example.finflowbackend.merchant.dto.ClearingFeeRateView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 清分费率计算器（S23）。只计算，不动账。
 * 取整规则（设计确认）：平台手续费与渠道成本各自向下取整（整数除法天然 floor），
 * 商户得 = 支付金额 − 平台手续费 − 渠道成本，余数全部归商户，三方向之和恒等于支付金额。
 * 手续费口径：比例部分（万分比）+ 固定费（分，每笔加收）；
 * 阶梯模式按支付金额命中「amount_from ≤ 金额」的最大档（固定费仍叠加）。
 * 商户未配置费率 → 平台手续费 0（纯计算器不读配置；默认费率兜底由清分消费者按配置中心 S37 处理）。
 */
@Component
public class FeeCalculator {

    private static final Logger log = LoggerFactory.getLogger(FeeCalculator.class);
    private static final long BPS_BASE = 10_000L;

    /** 分账结果：三项之和恒等于支付金额。 */
    public record Split(long merchantIncome, long platformFee, long channelCost) {
    }

    /**
     * 多方分账。
     *
     * @param payAmount     支付金额（分，正数）
     * @param merchantRate  商户费率视图（merchant 模块提供，可未配置）
     * @param channelCostBps 渠道成本费率（万分比，null 视为 0）
     */
    public Split split(long payAmount, ClearingFeeRateView merchantRate, Long channelCostBps) {
        long channelCost = channelCostBps == null ? 0L : payAmount * channelCostBps / BPS_BASE;
        long platformFee = platformFee(payAmount, merchantRate);
        long merchantIncome = payAmount - platformFee - channelCost;
        if (merchantIncome < 0) {
            // 费率配置异常（商户费率低于渠道成本）；清分照记，结算前人工介入
            log.warn("[清分] 商户应得为负，费率配置需人工核查: payAmount={}, platformFee={}, channelCost={}",
                    payAmount, platformFee, channelCost);
        }
        return new Split(merchantIncome, platformFee, channelCost);
    }

    private long platformFee(long payAmount, ClearingFeeRateView rate) {
        long bps = resolveRateBps(payAmount, rate);
        long proportional = bps <= 0 ? 0L : payAmount * bps / BPS_BASE;
        long fixed = rate == null || rate.fixedFee() == null ? 0L : rate.fixedFee();
        return proportional + fixed;
    }

    /** 阶梯模式取命中档位费率；普通模式取 rate_bps；未配置返回 0。 */
    private long resolveRateBps(long payAmount, ClearingFeeRateView rate) {
        if (rate == null) {
            log.warn("[清分] 商户未配置费率，平台手续费按 0 计");
            return 0L;
        }
        if (ClearingFeeRateView.MODE_TIERED.equals(rate.mode()) && !rate.tiers().isEmpty()) {
            return rate.tiers().stream()
                    .filter(t -> t.amountFrom() != null && t.amountFrom() <= payAmount)
                    .max(java.util.Comparator.comparing(ClearingFeeRateView.Tier::amountFrom))
                    .map(ClearingFeeRateView.Tier::rateBps)
                    .orElseGet(() -> {
                        log.warn("[清分] 阶梯未覆盖金额（最低档非 0 起步），平台手续费按 0 计: payAmount={}", payAmount);
                        return 0L;
                    });
        }
        return rate.rateBps() == null ? 0L : rate.rateBps();
    }
}
