package org.example.finflowbackend.refund.api;

/**
 * refund 模块对外能力（M12 facade）：商户 OpenAPI 发起退款与按退款单号查询。
 * 后台管理能力留在模块内部的 {@code RefundService}。
 */
public interface RefundApi {

    /**
     * 发起退款（调用方须先校验订单归属），编排与后台发起完全一致：
     * 占用可退额度 → 落退款单 → 渠道受理，失败释放额度。
     */
    RefundOrderSnapshot createRefund(Long orderId, long amount, String reason, String operator);

    /** 按平台退款单号查询；不存在抛 REFUND_NOT_FOUND。 */
    RefundOrderSnapshot findByRefundNo(String refundNo);
}
