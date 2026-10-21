package org.example.finflowbackend.merchant.dto;

import java.util.List;

/**
 * 清分用费率视图（S23）。两种模式：
 * TIERED —— tiers 非空，按支付金额命中「amount_from ≤ 金额」的最大档费率（固定费仍叠加）；
 * PLAIN  —— 无阶梯，用 rate_bps（可空）+ fixed_fee（可空），均空时平台手续费为 0。
 */
public record ClearingFeeRateView(Long merchantId, String mode, Long rateBps, Long fixedFee,
                                  List<Tier> tiers) {

    public static final String MODE_TIERED = "TIERED";
    public static final String MODE_PLAIN = "PLAIN";

    /** 阶梯档：amountFrom 含下限（分），rateBps 万分比。 */
    public record Tier(Long amountFrom, Long rateBps) {
    }
}
