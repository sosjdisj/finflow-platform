package org.example.fakechannel.web.dto;

/** 退款查询响应：返回账本当前状态。 */
public record RefundQueryResponse(String channelRefundNo, String platformRefundNo, String channelOrderNo,
                                  long amount, String status) {
}
