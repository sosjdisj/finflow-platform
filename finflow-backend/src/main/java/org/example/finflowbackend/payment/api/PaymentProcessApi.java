package org.example.finflowbackend.payment.api;

/**
 * 支付受理编排的对外能力（M03 / M12 facade）：下单受理（含幂等）与风控人工审核结论回写。
 */
public interface PaymentProcessApi {

    /**
     * 建单并立即受理（M12 OpenAPI 下单复用同一编排）：建单幂等（重复 merchantOrderNo 返回原单，
     * 已受理过的不重复受理），随后风控决策 + 渠道受理。
     */
    PaymentOrderResponse createAndProcess(PaymentOrderCreateRequest request);

    /** 风控 REVIEW 订单人工处理：通过回 PENDING 并直接渠道路由，拒绝置 FAILED。 */
    void resumeAfterReview(Long orderId, boolean approved, String operator, String reason);
}
