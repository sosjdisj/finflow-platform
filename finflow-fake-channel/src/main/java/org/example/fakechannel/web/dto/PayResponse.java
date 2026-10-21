package org.example.fakechannel.web.dto;

/** 支付受理响应。 */
public record PayResponse(String channelOrderNo, String status) {
}
