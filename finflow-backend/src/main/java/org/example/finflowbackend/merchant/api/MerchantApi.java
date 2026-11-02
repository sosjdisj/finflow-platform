package org.example.finflowbackend.merchant.api;

/**
 * merchant 模块对外能力（M03 facade）：商户详情读取。
 * 建商户、审核、费率维护等后台能力留在模块内部服务接口。
 */
public interface MerchantApi {

    /** 商户详情（基本信息 + 状态留痕）；不存在抛 MERCHANT_NOT_FOUND。 */
    MerchantDetailResponse getDetail(Long id);
}
