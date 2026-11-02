package org.example.finflowbackend.payment.api;

/**
 * 渠道回调命令：channel 模块解析渠道报文后交给 payment 的回调入口，
 * 平台订单解析、幂等去重与状态推进都在 payment 内完成。
 */
public record ChannelCallbackCommand(String channelCode, boolean refund, boolean success,
                                     String platformOrderNo, String channelOrderNo,
                                     String channelRefundNo, String refundNo, String message) {
}
