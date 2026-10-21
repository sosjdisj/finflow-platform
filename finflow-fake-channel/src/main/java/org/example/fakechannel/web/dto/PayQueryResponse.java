package org.example.fakechannel.web.dto;

/** 支付查询响应：返回账本当前状态。 */
public record PayQueryResponse(String channelOrderNo, String platformOrderNo, String merchantOrderNo,
                               long amount, String status) {
}
