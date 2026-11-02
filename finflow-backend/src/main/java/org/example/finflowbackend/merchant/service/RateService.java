package org.example.finflowbackend.merchant.service;

import org.example.finflowbackend.merchant.api.RateApi;
import org.example.finflowbackend.merchant.dto.MerchantRateLogResponse;
import org.example.finflowbackend.merchant.dto.MerchantRateResponse;
import org.example.finflowbackend.merchant.dto.MerchantRateSetRequest;

import java.util.List;

/**
 * 商户费率：查询、设置（含变更历史）。清分计算不在本服务。
 * 跨模块只经 {@link RateApi} 取费率视图。
 */
public interface RateService extends RateApi {

    /** 当前费率，未配置返回 null。 */
    MerchantRateResponse get(Long merchantId);

    /** 设置/修改费率，同一事务写变更历史（谁改的、改前改后）。 */
    MerchantRateResponse set(Long merchantId, MerchantRateSetRequest request);

    List<MerchantRateLogResponse> logs(Long merchantId);
}
