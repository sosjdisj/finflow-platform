package org.example.finflowbackend.clearing;

import org.example.finflowbackend.clearing.service.FeeCalculator;
import org.example.finflowbackend.merchant.dto.ClearingFeeRateView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 清分费率计算器单测（S23 验收锚点 + 取整规则）。
 * 恒等式：商户得 + 平台手续费 + 渠道成本 == 支付金额（无分差丢失）。
 */
class FeeCalculatorTest {

    private final FeeCalculator calculator = new FeeCalculator();

    /** 验收锚点：¥100、费率 2%、渠道成本 1% → 商户 9700 / 平台 200 / 渠道 100。 */
    @Test
    void acceptanceAnchorHundredYuan() {
        FeeCalculator.Split split = calculator.split(10000L, plain(200L, null), 100L);
        assertEquals(200L, split.platformFee());
        assertEquals(100L, split.channelCost());
        assertEquals(9700L, split.merchantIncome());
        assertEquals(10000L, sum(split));
    }

    /** 向下取整：999 分 × 2% = 19.98 → 19，渠道 9.99 → 9，商户得余数 971，分差不丢。 */
    @Test
    void floorsBothFeesAndMerchantGetsRemainder() {
        FeeCalculator.Split split = calculator.split(999L, plain(200L, null), 100L);
        assertEquals(19L, split.platformFee());
        assertEquals(9L, split.channelCost());
        assertEquals(971L, split.merchantIncome());
        assertEquals(999L, sum(split));
    }

    /** 固定手续费：每笔加收，与比例无关。 */
    @Test
    void fixedFeeOnly() {
        FeeCalculator.Split split = calculator.split(10000L, plain(null, 500L), 100L);
        assertEquals(500L, split.platformFee());
        assertEquals(100L, split.channelCost());
        assertEquals(9400L, split.merchantIncome());
        assertEquals(10000L, sum(split));
    }

    /** 比例 + 固定并存：比例部分 + 每笔固定加收（2% of ¥100 = 200 分，+ 300 分固定）。 */
    @Test
    void proportionalPlusFixed() {
        FeeCalculator.Split split = calculator.split(10000L, plain(200L, 300L), 100L);
        assertEquals(500L, split.platformFee());
        assertEquals(9400L, split.merchantIncome());
        assertEquals(10000L, sum(split));
    }

    /** 阶梯：命中「amount_from ≤ 支付金额」的最大档（6000 命中 5000 档 1%）。 */
    @Test
    void tieredRateHitsHighestMatchingTier() {
        ClearingFeeRateView tiered = new ClearingFeeRateView(1L, ClearingFeeRateView.MODE_TIERED,
                999L, null, List.of(
                new ClearingFeeRateView.Tier(0L, 300L),
                new ClearingFeeRateView.Tier(1000L, 200L),
                new ClearingFeeRateView.Tier(5000L, 100L)));
        FeeCalculator.Split split = calculator.split(6000L, tiered, 100L);
        assertEquals(60L, split.platformFee());
        assertEquals(60L, split.channelCost());
        assertEquals(5880L, split.merchantIncome());
        assertEquals(6000L, sum(split));
    }

    /** 阶梯低档：800 分命中 0 起步档 3%。 */
    @Test
    void tieredRateHitsBaseTier() {
        ClearingFeeRateView tiered = new ClearingFeeRateView(1L, ClearingFeeRateView.MODE_TIERED,
                null, null, List.of(
                new ClearingFeeRateView.Tier(0L, 300L),
                new ClearingFeeRateView.Tier(1000L, 200L)));
        FeeCalculator.Split split = calculator.split(800L, tiered, null);
        assertEquals(24L, split.platformFee());
        assertEquals(0L, split.channelCost());
        assertEquals(776L, split.merchantIncome());
        assertEquals(800L, sum(split));
    }

    /** 商户未配置费率：平台手续费 0（向商户倾斜），渠道成本照收。 */
    @Test
    void missingMerchantRateYieldsZeroPlatformFee() {
        FeeCalculator.Split split = calculator.split(10000L, null, 100L);
        assertEquals(0L, split.platformFee());
        assertEquals(100L, split.channelCost());
        assertEquals(9900L, split.merchantIncome());
        assertEquals(10000L, sum(split));
    }

    /** 渠道成本未配置：按 0 计。 */
    @Test
    void missingChannelCostYieldsZero() {
        FeeCalculator.Split split = calculator.split(10000L, plain(200L, null), null);
        assertEquals(200L, split.platformFee());
        assertEquals(0L, split.channelCost());
        assertEquals(9800L, split.merchantIncome());
        assertEquals(10000L, sum(split));
    }

    private long sum(FeeCalculator.Split split) {
        return split.merchantIncome() + split.platformFee() + split.channelCost();
    }

    private ClearingFeeRateView plain(Long rateBps, Long fixedFee) {
        return new ClearingFeeRateView(1L, ClearingFeeRateView.MODE_PLAIN, rateBps, fixedFee, List.of());
    }
}
