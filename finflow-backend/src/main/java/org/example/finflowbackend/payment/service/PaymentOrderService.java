package org.example.finflowbackend.payment.service;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.payment.api.PaymentOrderApi;
import org.example.finflowbackend.payment.api.PaymentOrderResponse;
import org.example.finflowbackend.payment.api.PaymentOrderCreateRequest;
import org.example.finflowbackend.payment.dto.PaymentOrderDetailResponse;

import java.time.LocalDateTime;

/**
 * 支付订单管理。状态推进（渠道回调、退款等）由后续步骤在此接口上扩展。
 * 跨模块只经 {@link PaymentOrderApi} 调用，建单与分页等后台能力留在本接口。
 */
public interface PaymentOrderService extends PaymentOrderApi {

    PaymentOrderResponse create(PaymentOrderCreateRequest request);

    PaymentOrderDetailResponse getDetail(Long id);

    /**
     * 管理端分页查询。金额条件单位为分，时间条件为创建时间闭区间；条件均为可选。
     */
    PageResponse<PaymentOrderResponse> page(long page, long size, Long merchantId, String status, String keyword,
                                            LocalDateTime createdFrom, LocalDateTime createdTo,
                                            Long minAmount, Long maxAmount);
}
