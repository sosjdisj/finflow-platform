package org.example.fakechannel.web.dto;

/** 支付受理请求。 */
public record PayRequest(String platformOrderNo, String merchantOrderNo, long amount,
                         String subject, String paymentMethod) {
}
