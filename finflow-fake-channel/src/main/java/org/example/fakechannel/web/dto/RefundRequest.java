package org.example.fakechannel.web.dto;

/** 退款受理请求。 */
public record RefundRequest(String channelOrderNo, String platformRefundNo, long amount) {
}
