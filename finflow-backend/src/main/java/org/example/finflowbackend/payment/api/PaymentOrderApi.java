package org.example.finflowbackend.payment.api;

/**
 * payment 订单对外能力（M03 facade）：读取、状态推进、退款额度占用与释放。
 * 建单与分页等后台管理能力留在模块内部的 {@code PaymentOrderService}。
 */
public interface PaymentOrderApi {

    /** 读取支付订单。 */
    PaymentOrderResponse get(Long id);

    /** 按平台订单号取订单 ID；不存在返回 null。 */
    Long findIdByPlatformOrderNo(String platformOrderNo);

    /** 按平台订单号读取订单（M12 OpenAPI 查单）；不存在抛 ORDER_NOT_FOUND。 */
    PaymentOrderResponse findByPlatformOrderNo(String platformOrderNo);

    /**
     * 商户主动关单（M12 OpenAPI）：仅 CREATED / PENDING → CLOSED，由状态机校验。
     * 订单不存在或不属于该商户时统一抛 ORDER_NOT_FOUND，避免越权探测。
     */
    void closeByMerchant(String platformOrderNo, Long merchantId, String operator);

    /** 状态推进：校验合法转换、更新状态并写留痕，同事务。 */
    void changeStatus(Long orderId, String toStatus, String reason, String operator);

    /** 退款防超退：行锁读订单 → 校验 → 占用额度，首笔退款联动 PAID → REFUNDING。 */
    PaymentOrderSnapshot reserveRefundQuota(Long orderId, long refundAmount, String operator);

    /** 退款失败释放占用额度（行锁内回退 refunded_amount）。 */
    void releaseRefundQuota(Long orderId, long refundAmount);

    /** 退款成功后调用：累计退满则 REFUNDING → REFUNDED。 */
    void markFullyRefundedIfNeeded(Long orderId, String refundNo, String operator);
}
