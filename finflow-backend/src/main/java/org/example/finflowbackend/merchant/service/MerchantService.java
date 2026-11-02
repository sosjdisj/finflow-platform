package org.example.finflowbackend.merchant.service;

import org.example.finflowbackend.common.api.PageResponse;
import org.example.finflowbackend.merchant.api.MerchantApi;
import org.example.finflowbackend.merchant.api.MerchantResponse;
import org.example.finflowbackend.merchant.dto.MerchantCreateRequest;
import org.example.finflowbackend.merchant.dto.MerchantReviewRequest;
import org.example.finflowbackend.merchant.dto.MerchantStatusChangeRequest;
import org.example.finflowbackend.merchant.dto.MerchantUpdateRequest;

/**
 * 商户管理。跨模块只经 {@link MerchantApi} 调用，后台管理能力留在本接口。
 */
public interface MerchantService extends MerchantApi {

    MerchantResponse create(MerchantCreateRequest request);

    PageResponse<MerchantResponse> page(long page, long size, String status, String keyword);

    MerchantResponse update(Long id, MerchantUpdateRequest request);

    MerchantResponse changeStatus(Long id, MerchantStatusChangeRequest request);

    /** 审核：通过 → ACTIVE，驳回 → CLOSED；PENDING 商户自动先转「审核中」。 */
    MerchantResponse review(Long id, MerchantReviewRequest request);
}
