package org.example.finflowbackend.merchant.service;

import org.example.finflowbackend.merchant.dto.ClearingFeeRateView;
import org.example.finflowbackend.merchant.dto.MerchantRateLogResponse;
import org.example.finflowbackend.merchant.dto.MerchantRateResponse;
import org.example.finflowbackend.merchant.dto.MerchantRateSetRequest;

import java.util.List;

/**
 * 商户费率：查询、设置（含变更历史）。清分计算不在本服务。
 */
public interface RateService {

    /** 当前费率，未配置返回 null。 */
    MerchantRateResponse get(Long merchantId);

    /**
     * 清分用费率视图（S23）：含阶梯档。阶梯存在时 mode=TIERED（费率按支付金额命中档位），
     * 否则 PLAIN（rateBps/fixedFee 可空，均空表示平台手续费 0）。
     * 商户不存在抛 43001。
     */
    ClearingFeeRateView getFeeRateView(Long merchantId);

    /** 设置/修改费率，同一事务写变更历史（谁改的、改前改后）。 */
    MerchantRateResponse set(Long merchantId, MerchantRateSetRequest request);

    List<MerchantRateLogResponse> logs(Long merchantId);
}
