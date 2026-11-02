package org.example.finflowbackend.merchant.api;

/**
 * merchant 费率对外能力（M03 facade）：清分用费率视图。
 * 费率设置与历史维护留在模块内部服务接口。
 */
public interface RateApi {

    /**
     * 清分用费率视图（S23）：含阶梯档。阶梯存在时 mode=TIERED，
     * 否则 PLAIN（rateBps/fixedFee 可空，均空表示平台手续费 0）。
     * 商户不存在抛 MERCHANT_NOT_FOUND。
     */
    ClearingFeeRateView getFeeRateView(Long merchantId);
}
