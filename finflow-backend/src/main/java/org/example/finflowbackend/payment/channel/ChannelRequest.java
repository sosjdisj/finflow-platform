package org.example.finflowbackend.payment.channel;

/**
 * 渠道请求（各方法通用字段超集）：
 * 受理/退款时填订单要素（platformOrderNo/merchantOrderNo 供渠道幂等与对账），
 * 查询时填渠道单号（channelOrderNo 亦承载退款查询的渠道退款单号）；退款时 refundNo 填平台退款单号。
 */
public record ChannelRequest(Long orderId, String platformOrderNo, String merchantOrderNo,
                             String channelOrderNo, long amount, String paymentMethod,
                             String subject, String refundNo) {
}
