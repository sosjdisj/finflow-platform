package org.example.finflowbackend.notification.event;

import org.example.finflowbackend.common.event.EventDeduplicationService;
import org.example.finflowbackend.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知消费者单测（S33 / M16）：事件路由到对应模板与商户、金额分转元展示、重复事件跳过；
 * M16 起商户 / 平台单号直接取自 payload（不再同步回查订单），对账异常仍走平台通知。
 */
class NotificationEventConsumerTest {

    private final EventDeduplicationService deduplicationService = mock(EventDeduplicationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationService notificationService = mock(NotificationService.class);

    private final NotificationEventConsumer consumer = new NotificationEventConsumer(
            deduplicationService, objectMapper, notificationService);

    @Test
    void paymentSucceededRoutesToMerchantFromPayload() {
        when(deduplicationService.tryConsume(anyString(), anyString())).thenReturn(true);

        consumer.onPaymentSucceeded("""
                {"eventId":"evt-pay-1","eventType":"PAYMENT_SUCCEEDED","occurredAt":"2026-10-18T15:00:00Z",
                 "payload":{"orderId":9,"merchantId":7,"platformOrderNo":"PO2026001","amount":10000,
                            "currency":"CNY","paidAt":"2026-10-18T15:00:00"}}
                """);

        ArgumentCaptor<String> templateCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> merchantCaptor = ArgumentCaptor.forClass(Long.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).sendMerchantNotification(templateCaptor.capture(),
                eq("evt-pay-1"), merchantCaptor.capture(), paramsCaptor.capture());
        assertEquals("PAYMENT_SUCCEEDED", templateCaptor.getValue());
        assertEquals(7L, merchantCaptor.getValue());
        assertEquals("PO2026001", paramsCaptor.getValue().get("platformOrderNo"));
        assertEquals("100.00", paramsCaptor.getValue().get("amount"));
        assertEquals("2026-10-18 15:00:00", paramsCaptor.getValue().get("paidAt"));
    }

    @Test
    void refundSucceededUsesPayloadMerchant() {
        when(deduplicationService.tryConsume(anyString(), anyString())).thenReturn(true);

        consumer.onRefundSucceeded("""
                {"eventId":"evt-refund-1","eventType":"REFUND_SUCCEEDED","occurredAt":"2026-10-18T15:00:00Z",
                 "payload":{"orderId":9,"refundNo":"RF1","platformOrderNo":"PO2026001","merchantId":7,
                            "refundAmount":5000,"currency":"CNY","refundedAt":"2026-10-18T15:00:00"}}
                """);

        ArgumentCaptor<Long> merchantCaptor = ArgumentCaptor.forClass(Long.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).sendMerchantNotification(eq("REFUND_SUCCEEDED"), eq("evt-refund-1"),
                merchantCaptor.capture(), paramsCaptor.capture());
        assertEquals(7L, merchantCaptor.getValue());
        assertEquals("50.00", paramsCaptor.getValue().get("refundAmount"));
        assertEquals("2026-10-18 15:00:00", paramsCaptor.getValue().get("refundedAt"));
    }

    @Test
    void settlementSucceededFormatsDateAndAmount() {
        when(deduplicationService.tryConsume(anyString(), anyString())).thenReturn(true);

        consumer.onSettlementSucceeded("""
                {"eventId":"evt-settle-1","eventType":"SETTLEMENT_SUCCEEDED","occurredAt":"2026-10-18T15:00:00Z",
                 "payload":{"settleNo":"ST1","merchantId":7,"settleDate":"2026-10-17",
                            "totalAmount":1455000,"currency":"CNY","executedAt":"2026-10-18T15:00:00"}}
                """);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).sendMerchantNotification(eq("SETTLEMENT_SUCCEEDED"), eq("evt-settle-1"),
                eq(7L), paramsCaptor.capture());
        assertEquals("2026-10-17", paramsCaptor.getValue().get("settleDate"));
        assertEquals("14550.00", paramsCaptor.getValue().get("totalAmount"));
        assertEquals("2026-10-18 15:00:00", paramsCaptor.getValue().get("executedAt"));
    }

    @Test
    void riskInterceptedRoutesToMerchantFromPayload() {
        when(deduplicationService.tryConsume(anyString(), anyString())).thenReturn(true);

        consumer.onRiskIntercepted("""
                {"eventId":"evt-risk-1","eventType":"RISK_INTERCEPTED","occurredAt":"2026-10-18T15:00:00Z",
                 "payload":{"orderId":9,"merchantId":99,"platformOrderNo":"PO2026001",
                            "verdict":"REVIEW","totalScore":30,"reason":"风险分 30 达到审核阈值 30"}}
                """);

        ArgumentCaptor<Map<String, String>> paramsCaptor = paramsCaptor();
        verify(notificationService).sendMerchantNotification(eq("RISK_INTERCEPTED"), eq("evt-risk-1"),
                eq(99L), paramsCaptor.capture());
        assertEquals("PO2026001", paramsCaptor.getValue().get("platformOrderNo"));
        assertEquals("拦截审核", paramsCaptor.getValue().get("action"));
        assertEquals("30", paramsCaptor.getValue().get("score"));
        assertEquals("风险分 30 达到审核阈值 30", paramsCaptor.getValue().get("reasons"));
    }

    @Test
    void reconciliationAbnormalSendsPlatformNotification() {
        when(deduplicationService.tryConsume(anyString(), anyString())).thenReturn(true);

        consumer.onReconciliationAbnormal("""
                {"eventId":"evt-recon-1","eventType":"RECONCILIATION_DIFFERENCE","occurredAt":"2026-10-18T15:00:00Z",
                 "payload":{"taskId":100,"taskNo":"R20261018000001","billDate":"2026-10-17",
                            "channelCode":"MOCK_WALLET","differenceType":"AMOUNT_MISMATCH",
                            "platformAmount":10000,"channelAmount":9900,"count":2}}
                """);

        ArgumentCaptor<Map<String, String>> paramsCaptor = paramsCaptor();
        verify(notificationService).sendPlatformNotification(eq("RECONCILIATION_DIFFERENCE"),
                eq("evt-recon-1"), paramsCaptor.capture());
        assertEquals("R20261018000001", paramsCaptor.getValue().get("taskNo"));
        assertEquals("金额不一致", paramsCaptor.getValue().get("differenceType"));
        assertEquals("100.00", paramsCaptor.getValue().get("platformAmount"));
        assertEquals("99.00", paramsCaptor.getValue().get("channelAmount"));
    }

    @Test
    void duplicateEventSkipped() {
        when(deduplicationService.tryConsume(anyString(), anyString())).thenReturn(false);

        consumer.onRefundSucceeded("""
                {"eventId":"evt-dup","eventType":"REFUND_SUCCEEDED","occurredAt":"2026-10-18T15:00:00Z",
                 "payload":{"orderId":9,"refundNo":"RF1","platformOrderNo":"PO","merchantId":7,
                            "refundAmount":5000,"currency":"CNY","refundedAt":"2026-10-18T15:00:00"}}
                """);

        verify(notificationService, never()).sendMerchantNotification(any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, String>> paramsCaptor() {
        return ArgumentCaptor.forClass(Map.class);
    }
}
