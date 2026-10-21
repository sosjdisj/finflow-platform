package org.example.finflowbackend.payment.channel;

/**
 * 渠道退款结果处理接口：Mock 渠道异步回调退款结果时调用，
 * 由 refund 模块实现（payment 模块不反向依赖 refund 的 Bean，避免构造依赖环）。
 */
public interface RefundResultHandler {

    /**
     * 渠道退款结果回调：success=true 渠道退款成功，false 退款失败（message 为原因）。
     * 回调可能先于受理响应落库（渠道受理与回调竞态），故附带 channelCode 供补受理。
     */
    void onResult(Long orderId, String channelCode, String refundNo, String channelRefundNo,
                  boolean success, String message);
}