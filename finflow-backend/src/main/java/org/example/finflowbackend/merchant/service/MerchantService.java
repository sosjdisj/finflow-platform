package org.example.finflowbackend.merchant.service;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.merchant.dto.MerchantCreateRequest;
import org.example.finflowbackend.merchant.dto.MerchantDetailResponse;
import org.example.finflowbackend.merchant.dto.MerchantResponse;
import org.example.finflowbackend.merchant.dto.MerchantReviewRequest;
import org.example.finflowbackend.merchant.dto.MerchantStatusChangeRequest;
import org.example.finflowbackend.merchant.dto.MerchantUpdateRequest;

/**
 * 商户管理。供 payment 等模块跨模块调用的入口也收敛在此接口。
 */
public interface MerchantService {

    MerchantResponse create(MerchantCreateRequest request);

    PageResponse<MerchantResponse> page(long page, long size, String status, String keyword);

    MerchantDetailResponse getDetail(Long id);

    MerchantResponse update(Long id, MerchantUpdateRequest request);

    MerchantResponse changeStatus(Long id, MerchantStatusChangeRequest request);

    /** 审核：通过 → ACTIVE，驳回 → CLOSED；PENDING 商户自动先转「审核中」。 */
    MerchantResponse review(Long id, MerchantReviewRequest request);
}
